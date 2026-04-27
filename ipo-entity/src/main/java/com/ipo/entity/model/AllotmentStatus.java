package com.ipo.entity.model;

/**
 * AllotmentStatus Enum
 *
 * Represents the lifecycle status of an allotment process.
 */
public enum AllotmentStatus {
    /**
     * Allotment record created, not yet started
     */
    PENDING,

    /**
     * Allotment process is currently running
     */
    IN_PROGRESS,

    /**
     * Allotment successfully completed
     */
    COMPLETED,

    /**
     * Allotment failed - all shares not allocated or error occurred
     */
    FAILED
}
