package com.ipo.service.kafka;

import com.ipo.service.event.KafkaEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class KafkaProducerService {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Send event to Kafka topic
     */
    public CompletableFuture<String> sendEvent(String topic, String eventType, Object payload) {
        return sendEvent(topic, eventType, payload, null);
    }

    /**
     * Send event to Kafka topic with partition key
     */
    public CompletableFuture<String> sendEvent(String topic, String eventType, Object payload, String partitionKey) {
        try {
            KafkaEvent event = KafkaEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .topic(topic)
                    .timestamp(LocalDateTime.now())
                    .payload(payload)
                    .source("ipo-service")
                    .version("1.0")
                    .build();

            Message<KafkaEvent> message = MessageBuilder
                    .withPayload(event)
                    .setHeader(KafkaHeaders.TOPIC, topic)
                    .setHeader(KafkaHeaders.MESSAGE_KEY, partitionKey != null ? partitionKey : UUID.randomUUID().toString())
                    .build();

            CompletableFuture<String> future = new CompletableFuture<>();

            kafkaTemplate.send(message).addCallback(
                    result -> {
                        log.info("Event sent successfully to topic: {}, eventType: {}, eventId: {}",
                                topic, eventType, event.getEventId());
                        future.complete(event.getEventId());
                    },
                    ex -> {
                        log.error("Failed to send event to topic: {}, eventType: {}", topic, eventType, ex);
                        future.completeExceptionally(ex);
                    }
            );

            return future;
        } catch (Exception e) {
            log.error("Error sending event to topic: {}", topic, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Send IPO created event
     */
    public CompletableFuture<String> sendIPOCreatedEvent(Object ipoData) {
        return sendEvent("ipo-created", "IPO_CREATED", ipoData);
    }

    /**
     * Send IPO updated event
     */
    public CompletableFuture<String> sendIPOUpdatedEvent(Object ipoData) {
        return sendEvent("ipo-updated", "IPO_UPDATED", ipoData);
    }

    /**
     * Send investment created event
     */
    public CompletableFuture<String> sendInvestmentCreatedEvent(Object investmentData) {
        return sendEvent("investment-created", "INVESTMENT_CREATED", investmentData);
    }

    /**
     * Send investment processed event
     */
    public CompletableFuture<String> sendInvestmentProcessedEvent(Object investmentData) {
        return sendEvent("investment-processed", "INVESTMENT_PROCESSED", investmentData);
    }

    /**
     * Send application submitted event
     */
    public CompletableFuture<String> sendApplicationSubmittedEvent(Object applicationData) {
        return sendEvent("application-submitted", "APPLICATION_SUBMITTED", applicationData);
    }

    /**
     * Send IPO closed event (triggers allotment)
     */
    public CompletableFuture<String> sendIPOClosedEvent(Object ipoData, String partitionKey) {
        return sendEvent("ipo-closed", "IPO_CLOSED", ipoData, partitionKey);
    }

    /**
     * Send allotment completed event
     */
    public CompletableFuture<String> sendAllotmentCompletedEvent(Object allotmentData, String partitionKey) {
        return sendEvent("allotment-completed", "ALLOTMENT_COMPLETED", allotmentData, partitionKey);
    }

    /**
     * Send allotment failed event
     */
    public CompletableFuture<String> sendAllotmentFailedEvent(Object failureData, String partitionKey) {
        return sendEvent("allotment-failed", "ALLOTMENT_FAILED", failureData, partitionKey);
    }
}
