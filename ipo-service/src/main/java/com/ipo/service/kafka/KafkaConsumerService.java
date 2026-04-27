package com.ipo.service.kafka;

import com.ipo.service.event.KafkaEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KafkaConsumerService {

    /**
     * Listen to IPO created events
     */
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopic = "true",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(
            topics = "ipo-created",
            groupId = "ipo-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleIPOCreatedEvent(KafkaEvent event) {
        try {
            log.info("Received IPO_CREATED event: eventId={}, ipoId={}", event.getEventId(), event.getPayload());
            // Process IPO created event
            // Add business logic here
        } catch (Exception e) {
            log.error("Error processing IPO_CREATED event: {}", event.getEventId(), e);
            throw e;
        }
    }

    /**
     * Listen to IPO updated events
     */
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopic = "true",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(
            topics = "ipo-updated",
            groupId = "ipo-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleIPOUpdatedEvent(KafkaEvent event) {
        try {
            log.info("Received IPO_UPDATED event: eventId={}, ipoId={}", event.getEventId(), event.getPayload());
            // Process IPO updated event
        } catch (Exception e) {
            log.error("Error processing IPO_UPDATED event: {}", event.getEventId(), e);
            throw e;
        }
    }

    /**
     * Listen to investment created events
     */
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopic = "true",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(
            topics = "investment-created",
            groupId = "ipo-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleInvestmentCreatedEvent(KafkaEvent event) {
        try {
            log.info("Received INVESTMENT_CREATED event: eventId={}, investmentId={}", event.getEventId(), event.getPayload());
            // Process investment created event
        } catch (Exception e) {
            log.error("Error processing INVESTMENT_CREATED event: {}", event.getEventId(), e);
            throw e;
        }
    }

    /**
     * Listen to investment processed events
     */
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopic = "true",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(
            topics = "investment-processed",
            groupId = "ipo-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleInvestmentProcessedEvent(KafkaEvent event) {
        try {
            log.info("Received INVESTMENT_PROCESSED event: eventId={}, investmentId={}", event.getEventId(), event.getPayload());
            // Process investment processed event
        } catch (Exception e) {
            log.error("Error processing INVESTMENT_PROCESSED event: {}", event.getEventId(), e);
            throw e;
        }
    }

    /**
     * Listen to application submitted events
     */
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopic = "true",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(
            topics = "application-submitted",
            groupId = "ipo-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleApplicationSubmittedEvent(KafkaEvent event) {
        try {
            log.info("Received APPLICATION_SUBMITTED event: eventId={}, applicationId={}", event.getEventId(), event.getPayload());
            // Process application submitted event
        } catch (Exception e) {
            log.error("Error processing APPLICATION_SUBMITTED event: {}", event.getEventId(), e);
            throw e;
        }
    }
}
