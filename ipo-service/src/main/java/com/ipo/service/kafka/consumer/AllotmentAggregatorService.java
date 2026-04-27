package com.ipo.service.kafka.consumer;

import com.ipo.entity.model.IPO;
import com.ipo.entity.model.IPOStatus;
import com.ipo.entity.repository.IPORepository;
import com.ipo.entity.repository.KafkaEventProcessedRepository;
import com.ipo.entity.model.KafkaEventProcessed;
import com.ipo.service.event.KafkaEvent;
import com.ipo.service.event.dto.AllotmentCompletedEventDto;
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
import java.util.Map;
import java.util.Optional;

/**
 * Aggregates allotment completion events and performs final cleanup
 * Updates IPO status to COMPLETED when allotment finishes
 */
@Slf4j
@Service
public class AllotmentAggregatorService {

    @Autowired
    private IPORepository ipoRepository;

    @Autowired
    private KafkaEventProcessedRepository eventProcessedRepository;

    /**
     * Process ALLOTMENT_COMPLETED events from allotment-completed topic
     * Updates IPO status to COMPLETED
     */
    @RetryableTopic(
            attempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 15000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            retryTopicSuffix = "-retry",
            dltTopicSuffix = "-dlt",
            include = {Exception.class}
    )
    @KafkaListener(
            topics = "allotment-completed",
            groupId = "allotment-aggregator",
            concurrency = "1"
    )
    @Transactional
    public void processAllotmentCompleted(
            @Payload KafkaEvent kafkaEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        long startTime = System.currentTimeMillis();
        String eventId = kafkaEvent.getEventId();

        try {
            log.info("Processing ALLOTMENT_COMPLETED event: eventId={}, partition={}, offset={}",
                eventId, partition, offset);

            // Check idempotency
            Optional<KafkaEventProcessed> existing = eventProcessedRepository.findByEventId(eventId);
            if (existing.isPresent() && "SUCCESS".equals(existing.get().getStatus())) {
                log.info("Allotment completion event already processed: eventId={}", eventId);
                recordEventProcessed(eventId, kafkaEvent, partition, offset, "SKIPPED",
                    System.currentTimeMillis() - startTime, 0);
                return;
            }

            // Extract completion data
            AllotmentCompletedEventDto eventData = extractAllotmentCompletedEvent(kafkaEvent);

            // Update IPO status to COMPLETED
            IPO ipo = ipoRepository.findById(eventData.getIpoId())
                .orElseThrow(() -> new IllegalArgumentException("IPO not found: " + eventData.getIpoId()));

            if (ipo.getStatus() != IPOStatus.ALLOTMENT) {
                log.warn("IPO not in ALLOTMENT status, current status: {}", ipo.getStatus());
            } else {
                ipo.setStatus(IPOStatus.COMPLETED);
                ipoRepository.save(ipo);
                log.info("Updated IPO status to COMPLETED: ipoId={}, allotmentNumber={}",
                    eventData.getIpoId(), eventData.getAllotmentNumber());
            }

            // Record successful processing
            recordEventProcessed(eventId, kafkaEvent, partition, offset, "SUCCESS",
                System.currentTimeMillis() - startTime, 0);

            // Log summary
            log.info("Allotment completion aggregated: ipoId={}, applicationsProcessed={}, " +
                    "sharesAllocated={}, duration={}ms",
                eventData.getIpoId(),
                eventData.getTotalApplicationsProcessed(),
                eventData.getTotalSharesAllocated(),
                eventData.getProcessingDurationMs());

        } catch (Exception e) {
            log.error("Error processing ALLOTMENT_COMPLETED event: eventId={}", eventId, e);

            // Record failed processing
            int retryCount = existing
                .map(ep -> ep.getRetryCount() != null ? ep.getRetryCount() : 0)
                .orElse(0);

            recordEventProcessed(eventId, kafkaEvent, partition, offset, "FAILED",
                System.currentTimeMillis() - startTime, retryCount + 1,
                e.getMessage());

            throw new RuntimeException("Allotment completion aggregation failed", e);
        }
    }

    /**
     * Process ALLOTMENT_FAILED events from allotment-failed topic
     * Marks IPO allotment as failed for retry
     */
    @RetryableTopic(
            attempts = 2,
            backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 15000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            retryTopicSuffix = "-retry",
            dltTopicSuffix = "-dlt",
            include = {Exception.class}
    )
    @KafkaListener(
            topics = "allotment-failed",
            groupId = "allotment-aggregator",
            concurrency = "1"
    )
    @Transactional
    public void processAllotmentFailed(
            @Payload KafkaEvent kafkaEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        long startTime = System.currentTimeMillis();
        String eventId = kafkaEvent.getEventId();

        try {
            log.error("Processing ALLOTMENT_FAILED event: eventId={}, partition={}, offset={}",
                eventId, partition, offset);

            // Check idempotency
            Optional<KafkaEventProcessed> existing = eventProcessedRepository.findByEventId(eventId);
            if (existing.isPresent() && "SUCCESS".equals(existing.get().getStatus())) {
                log.info("Allotment failure event already processed: eventId={}", eventId);
                recordEventProcessed(eventId, kafkaEvent, partition, offset, "SKIPPED",
                    System.currentTimeMillis() - startTime, 0);
                return;
            }

            // Extract failure data
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> failureData =
                (java.util.Map<String, Object>) kafkaEvent.getPayload();
            Long ipoId = ((Number) failureData.get("ipoId")).longValue();
            String errorMessage = (String) failureData.get("errorMessage");

            // Update IPO status - don't change it, leave it for admin retry
            IPO ipo = ipoRepository.findById(ipoId)
                .orElseThrow(() -> new IllegalArgumentException("IPO not found: " + ipoId));

            log.warn("Allotment failed for IPO {}: {}", ipoId, errorMessage);

            // Record event
            recordEventProcessed(eventId, kafkaEvent, partition, offset, "SUCCESS",
                System.currentTimeMillis() - startTime, 0);

        } catch (Exception e) {
            log.error("Error processing ALLOTMENT_FAILED event: eventId={}", eventId, e);

            int retryCount = existing
                .map(ep -> ep.getRetryCount() != null ? ep.getRetryCount() : 0)
                .orElse(0);

            recordEventProcessed(eventId, kafkaEvent, partition, offset, "FAILED",
                System.currentTimeMillis() - startTime, retryCount + 1,
                e.getMessage());

            throw new RuntimeException("Allotment failure handling failed", e);
        }
    }

    /**
     * Extract AllotmentCompletedEventDto from KafkaEvent payload
     */
    private AllotmentCompletedEventDto extractAllotmentCompletedEvent(KafkaEvent kafkaEvent) {
        try {
            if (kafkaEvent.getPayload() instanceof AllotmentCompletedEventDto) {
                return (AllotmentCompletedEventDto) kafkaEvent.getPayload();
            }

            if (kafkaEvent.getPayload() instanceof java.util.Map) {
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) kafkaEvent.getPayload();
                return AllotmentCompletedEventDto.builder()
                    .allotmentId(((Number) map.get("allotmentId")).longValue())
                    .ipoId(((Number) map.get("ipoId")).longValue())
                    .ipoNumber((String) map.get("ipoNumber"))
                    .totalApplicationsProcessed(((Number) map.get("totalApplicationsProcessed")).longValue())
                    .totalSharesAllocated(((Number) map.get("totalSharesAllocated")).longValue())
                    .completedAt((LocalDateTime) map.get("completedAt"))
                    .processingDurationMs(((Number) map.get("processingDurationMs")).longValue())
                    .status((String) map.get("status"))
                    .allotmentNumber((String) map.get("allotmentNumber"))
                    .build();
            }

            throw new IllegalArgumentException("Cannot deserialize payload to AllotmentCompletedEventDto");
        } catch (Exception e) {
            log.error("Error extracting ALLOTMENT_COMPLETED event data", e);
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
                    .consumerGroup("allotment-aggregator")
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
}
