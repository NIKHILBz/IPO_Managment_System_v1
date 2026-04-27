package com.ipo.service.kafka.consumer;

import com.ipo.entity.model.IPO;
import com.ipo.entity.model.IPOStatus;
import com.ipo.entity.repository.IPORepository;
import com.ipo.entity.repository.KafkaEventProcessedRepository;
import com.ipo.entity.model.KafkaEventProcessed;
import com.ipo.service.event.KafkaEvent;
import com.ipo.service.event.dto.IPOClosedEventDto;
import com.ipo.service.event.dto.AllotmentCompletedEventDto;
import com.ipo.service.kafka.KafkaProducerService;
import com.ipo.service.lock.DistributedLockService;
import com.ipo.service.service.allotment.AllotmentService;
import com.ipo.service.service.allotment.dto.AllotmentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Consumes IPO_CLOSED events and triggers fair lottery allotment
 * Ensures idempotency and handles failures gracefully
 */
@Slf4j
@Service
public class AllotmentConsumerService {

    @Autowired
    private AllotmentService allotmentService;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    @Autowired
    private DistributedLockService lockService;

    @Autowired
    private KafkaEventProcessedRepository eventProcessedRepository;

    @Autowired
    private IPORepository ipoRepository;

    private static final String ALLOTMENT_LOCK_PREFIX = "allotment:ipo:";
    private static final long ALLOTMENT_LOCK_DURATION_MS = 3600000;  // 1 hour
    private static final int DEFAULT_PAGE_SIZE = 10000;

    /**
     * Process IPO_CLOSED event from ipo-closed topic
     * Triggers fair lottery allotment for the IPO
     *
     * Partition key: ipoId (ensures same IPO always goes to same partition)
     * Retries: 3 times with exponential backoff (1s, 2s, 4s)
     */
    @RetryableTopic(
            attempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            retryTopicSuffix = "-retry",
            dltTopicSuffix = "-dlt",
            include = {Exception.class}
    )
    @KafkaListener(
            topics = "ipo-closed",
            groupId = "allotment-processor",
            concurrency = "3"
    )
    @Transactional
    public void processIPOClosed(
            @Payload KafkaEvent kafkaEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        long startTime = System.currentTimeMillis();
        String eventId = kafkaEvent.getEventId();
        String ipoId = null;

        try {
            log.info("Processing IPO_CLOSED event: eventId={}, partition={}, offset={}",
                eventId, partition, offset);

            // Step 1: Check idempotency - has this event already been processed?
            Optional<KafkaEventProcessed> existing = eventProcessedRepository.findByEventId(eventId);
            if (existing.isPresent() && "SUCCESS".equals(existing.get().getStatus())) {
                log.info("Event already processed successfully: eventId={}", eventId);
                recordEventProcessed(eventId, kafkaEvent, partition, offset, "SKIPPED",
                    System.currentTimeMillis() - startTime, 0);
                return;
            }

            // Step 2: Extract IPO ID and validate payload
            IPOClosedEventDto eventData = extractIPOClosedEvent(kafkaEvent);
            ipoId = eventData.getIpoId().toString();

            // Step 3: Acquire distributed lock (prevent concurrent allotment for same IPO)
            String lockKey = ALLOTMENT_LOCK_PREFIX + eventData.getIpoId();
            String lockToken = lockService.acquireLock(lockKey, ALLOTMENT_LOCK_DURATION_MS);

            if (lockToken == null) {
                log.warn("Could not acquire allotment lock for IPO: {}, retrying later",
                    eventData.getIpoId());
                throw new RuntimeException("Could not acquire allotment lock - IPO may already be processing");
            }

            try {
                // Step 4: Verify IPO status is CLOSED
                IPO ipo = ipoRepository.findById(eventData.getIpoId())
                    .orElseThrow(() -> new IllegalArgumentException("IPO not found: " + eventData.getIpoId()));

                if (ipo.getStatus() != IPOStatus.CLOSED) {
                    log.warn("IPO not in CLOSED status, current status: {}", ipo.getStatus());
                    recordEventProcessed(eventId, kafkaEvent, partition, offset, "SKIPPED",
                        System.currentTimeMillis() - startTime, 0);
                    return;
                }

                // Step 5: Execute fair lottery allotment
                log.info("Starting fair lottery allotment for IPO: {}", eventData.getIpoId());
                AllotmentResult allotmentResult = allotmentService.performFairLotteryAllotment(
                    eventData.getIpoId(),
                    DEFAULT_PAGE_SIZE
                );

                // Step 6: Publish allotment completed event
                AllotmentCompletedEventDto completedEvent = AllotmentCompletedEventDto.builder()
                    .allotmentId(allotmentResult.getAllotmentId())
                    .ipoId(eventData.getIpoId())
                    .ipoNumber(eventData.getIpoNumber())
                    .totalApplicationsProcessed(allotmentResult.getTotalApplicationsProcessed())
                    .totalSharesAllocated(allotmentResult.getTotalSharesAllocated())
                    .oversubscriptionRatio(allotmentResult.getOversubscriptionRatio())
                    .completedAt(allotmentResult.getCompletedAt())
                    .processingDurationMs(System.currentTimeMillis() - startTime)
                    .status("COMPLETED")
                    .allotmentNumber(allotmentResult.getAllotmentNumber())
                    .build();

                kafkaProducerService.sendAllotmentCompletedEvent(completedEvent, ipoId)
                    .thenAccept(eventSent -> {
                        log.info("Allotment completed event published: eventId={}, allotmentId={}",
                            eventSent, allotmentResult.getAllotmentId());
                    })
                    .exceptionally(ex -> {
                        log.error("Failed to publish allotment completed event", ex);
                        return null;
                    });

                // Step 7: Record successful processing
                recordEventProcessed(eventId, kafkaEvent, partition, offset, "SUCCESS",
                    System.currentTimeMillis() - startTime, 0);

                log.info("IPO allotment completed successfully: ipoId={}, allotmentId={}, " +
                        "applicationsProcessed={}, sharesAllocated={}",
                    eventData.getIpoId(),
                    allotmentResult.getAllotmentId(),
                    allotmentResult.getTotalApplicationsProcessed(),
                    allotmentResult.getTotalSharesAllocated());

            } finally {
                // Always release the lock
                lockService.releaseLock(lockKey, lockToken);
            }

        } catch (Exception e) {
            log.error("Error processing IPO_CLOSED event: eventId={}, ipoId={}", eventId, ipoId, e);

            // Publish allotment failed event
            try {
                kafkaProducerService.sendAllotmentFailedEvent(
                    new AllotmentFailureEventDto(ipoId, eventId, e.getMessage()),
                    ipoId
                );
            } catch (Exception publishEx) {
                log.error("Failed to publish allotment failed event", publishEx);
            }

            // Record failed processing (with retry count incremented)
            int retryCount = existing
                .map(ep -> ep.getRetryCount() != null ? ep.getRetryCount() : 0)
                .orElse(0);

            recordEventProcessed(eventId, kafkaEvent, partition, offset, "FAILED",
                System.currentTimeMillis() - startTime, retryCount + 1,
                e.getMessage());

            throw new RuntimeException("Allotment processing failed", e);
        }
    }

    /**
     * Extract IPOClosedEventDto from KafkaEvent payload
     */
    private IPOClosedEventDto extractIPOClosedEvent(KafkaEvent kafkaEvent) {
        try {
            // The payload is typically a LinkedHashMap or similar from JSON deserialization
            if (kafkaEvent.getPayload() instanceof IPOClosedEventDto) {
                return (IPOClosedEventDto) kafkaEvent.getPayload();
            }

            // If it's a Map, convert it manually
            if (kafkaEvent.getPayload() instanceof java.util.Map) {
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) kafkaEvent.getPayload();
                return IPOClosedEventDto.builder()
                    .ipoId(((Number) map.get("ipoId")).longValue())
                    .ipoNumber((String) map.get("ipoNumber"))
                    .totalSharesOffered(((Number) map.get("totalSharesOffered")).longValue())
                    .totalApplicationsReceived(((Number) map.get("totalApplicationsReceived")).longValue())
                    .totalSharesRequested(((Number) map.get("totalSharesRequested")).longValue())
                    .allotmentMethod((String) map.get("allotmentMethod"))
                    .build();
            }

            throw new IllegalArgumentException("Cannot deserialize payload to IPOClosedEventDto");
        } catch (Exception e) {
            log.error("Error extracting IPO_CLOSED event data", e);
            throw new RuntimeException("Failed to extract event data", e);
        }
    }

    /**
     * Record event processing for idempotency tracking
     */
    private void recordEventProcessed(String eventId, KafkaEvent event, int partition, long offset,
                                      String status, long processingDurationMs, int retryCount) {
        recordEventProcessed(eventId, event, partition, offset, status, processingDurationMs, retryCount, null);
    }

    /**
     * Record event processing with error message
     */
    private void recordEventProcessed(String eventId, KafkaEvent event, int partition, long offset,
                                      String status, long processingDurationMs, int retryCount,
                                      String errorMessage) {
        try {
            Optional<KafkaEventProcessed> existing = eventProcessedRepository.findByEventId(eventId);

            KafkaEventProcessed processed;
            if (existing.isPresent()) {
                processed = existing.get();
                processed.setStatus(status);
                processed.setRetryCount(retryCount);
                processed.setProcessingDurationMs(processingDurationMs);
                processed.setErrorMessage(errorMessage);
                processed.setProcessedAt(LocalDateTime.now());
            } else {
                processed = KafkaEventProcessed.builder()
                    .eventId(eventId)
                    .topic(event.getTopic())
                    .eventType(event.getEventType())
                    .status(status)
                    .partition(partition)
                    .offset(offset)
                    .consumerGroup("allotment-processor")
                    .processedAt(LocalDateTime.now())
                    .processingDurationMs(processingDurationMs)
                    .retryCount(retryCount)
                    .errorMessage(errorMessage)
                    .build();
            }

            eventProcessedRepository.save(processed);
        } catch (Exception e) {
            log.error("Failed to record event processed: eventId={}", eventId, e);
        }
    }

    /**
     * Helper DTO for allotment failure events
     */
    public static class AllotmentFailureEventDto {
        public String ipoId;
        public String eventId;
        public String errorMessage;

        public AllotmentFailureEventDto(String ipoId, String eventId, String errorMessage) {
            this.ipoId = ipoId;
            this.eventId = eventId;
            this.errorMessage = errorMessage;
        }
    }
}
