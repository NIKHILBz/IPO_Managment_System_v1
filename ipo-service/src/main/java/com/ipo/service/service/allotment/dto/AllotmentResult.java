package com.ipo.service.service.allotment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AllotmentResult DTO
 *
 * Response object for allotment completion.
 * Contains summary statistics of the allotment process.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllotmentResult {

    private Long allotmentId;
    private String allotmentNumber;
    private String status;
    private Long totalApplicationsProcessed;
    private Long totalSharesAllocated;
    private BigDecimal oversubscriptionRatio;
    private LocalDateTime completedAt;
    private String errorMessage;  // If status is FAILED

    public boolean isSuccessful() {
        return "COMPLETED".equals(status);
    }
}
