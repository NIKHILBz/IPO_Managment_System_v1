package com.ipo.entity.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import javax.persistence.*;

/**
 * Tracks processed Kafka events for idempotency
 * Prevents duplicate processing of the same event if redelivered
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "kafka_events_processed",
    indexes = {
        @Index(name = "idx_event_id", columnList = "event_id", unique = true),
        @Index(name = "idx_topic_timestamp", columnList = "topic, processed_at"),
        @Index(name = "idx_status", columnList = "status")
    }
)
public class KafkaEventProcessed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;  // UUID from KafkaEvent

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;  // Kafka topic name

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;  // e.g., "IPO_CLOSED", "ALLOTMENT_COMPLETED"

    @Column(name = "status", nullable = false, length = 20)
    private String status;  // "SUCCESS", "FAILED", "SKIPPED", "PROCESSING"

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;  // Error details if status = FAILED

    @Column(name = "partition", nullable = false)
    private Integer partition;  // Kafka partition

    @Column(name = "offset", nullable = false)
    private Long offset;  // Kafka offset

    @Column(name = "consumer_group", nullable = false, length = 100)
    private String consumerGroup;  // Consumer group that processed it

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;  // When this event was processed

    @Column(name = "processing_duration_ms")
    private Long processingDurationMs;  // How long processing took

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;  // Number of retries (0 for first attempt)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (processedAt == null) {
            processedAt = LocalDateTime.now();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (status == null) {
            status = "PROCESSING";
        }
    }
}
