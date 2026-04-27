package com.ipo.service.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event payload when allotment completes successfully
 * Published to allotment-completed topic
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllotmentCompletedEventDto {

    private Long allotmentId;

    private Long ipoId;

    private String ipoNumber;

    private Long totalApplicationsProcessed;

    private Long totalSharesAllocated;

    private BigDecimal oversubscriptionRatio;

    private BigDecimal averageAllocationPercentage;

    private LocalDateTime completedAt;

    private Long processingDurationMs;

    private String status;  // "COMPLETED", "PARTIAL", "FAILED"

    private String errorMessage;  // Null if successful

    private String allotmentNumber;  // Unique allotment identifier
}
