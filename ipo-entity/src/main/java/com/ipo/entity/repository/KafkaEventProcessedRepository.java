package com.ipo.entity.repository;

import com.ipo.entity.model.KafkaEventProcessed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface KafkaEventProcessedRepository extends JpaRepository<KafkaEventProcessed, Long> {

    /**
     * Find by event ID (for idempotency check)
     */
    Optional<KafkaEventProcessed> findByEventId(String eventId);

    /**
     * Check if event was already successfully processed
     */
    @Query("SELECT CASE WHEN COUNT(k) > 0 THEN true ELSE false END " +
           "FROM KafkaEventProcessed k WHERE k.eventId = :eventId AND k.status = 'SUCCESS'")
    boolean isEventProcessed(@Param("eventId") String eventId);

    /**
     * Find failed events for retrying
     */
    @Query("SELECT k FROM KafkaEventProcessed k WHERE k.status = 'FAILED' AND k.retryCount < :maxRetries " +
           "ORDER BY k.processedAt ASC")
    java.util.List<KafkaEventProcessed> findFailedEventsForRetry(@Param("maxRetries") Integer maxRetries);

    /**
     * Count processed events by topic
     */
    long countByTopic(String topic);

    /**
     * Count successful events by event type
     */
    long countByEventTypeAndStatus(String eventType, String status);

    /**
     * Find events processed after a timestamp (for auditing)
     */
    @Query("SELECT k FROM KafkaEventProcessed k WHERE k.processedAt > :since ORDER BY k.processedAt DESC")
    java.util.List<KafkaEventProcessed> findEventsSince(@Param("since") LocalDateTime since);
}
