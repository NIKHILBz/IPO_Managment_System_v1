package com.ipo.service.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event payload for allotment processing events
 * Published when allotment process starts (intermediate tracking)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllotmentProcessingEventDto {

    private Long allotmentId;

    private Long ipoId;

    private String ipoNumber;

    private Long totalApplicationsToProcess;

    private Long totalSharesAvailable;

    private Long totalSharesRequested;

    private BigDecimal oversubscriptionRatio;

    private Long randomSeed;  // For reproducibility

    private String allotmentMethod;

    private LocalDateTime startedAt;

    private Integer partition;  // Kafka partition processing this

    private Integer partitionCount;  // Total partitions for this allotment
}
