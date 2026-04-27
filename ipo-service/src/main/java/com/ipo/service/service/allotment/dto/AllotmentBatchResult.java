package com.ipo.service.service.allotment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AllotmentBatchResult DTO
 *
 * Result of processing a single batch of investments.
 * Used internally during batch processing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllotmentBatchResult {

    private int processedCount;  // Number of investments processed in this batch
    private long totalAllocated;  // Total shares allocated in this batch
    private int successCount;  // Investments successfully allocated
    private int failureCount;  // Investments that failed during allocation
}
