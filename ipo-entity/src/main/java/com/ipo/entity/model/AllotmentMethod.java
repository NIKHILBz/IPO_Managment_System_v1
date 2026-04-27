package com.ipo.entity.model;

/**
 * AllotmentMethod Enum
 *
 * Represents different allotment/allocation strategies.
 */
public enum AllotmentMethod {
    /**
     * Fair lottery - random allocation with deterministic seed
     * Good for: Preventing gaming, perceived fairness
     * Result: Some investors get 1 share, others get thousands
     */
    FAIR_LOTTERY,

    /**
     * Pro-rata allocation - proportional to request
     * Good for: Predictable, fair distribution
     * Result: Everyone gets same percentage of request
     */
    PRO_RATA,

    /**
     * Combination approach - pro-rata up to min threshold, lottery for remainder
     * Good for: Ensuring everyone gets something, then fair random for excess
     */
    COMBINATION
}
