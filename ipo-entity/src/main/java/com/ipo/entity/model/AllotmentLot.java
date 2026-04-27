package com.ipo.entity.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AllotmentLot Entity
 *
 * Represents individual lot allocation for each investor in an allotment batch.
 * One record per investor per allotment run.
 *
 * Example: Investment(Alice, IPO-1) -> AllotmentLot(600 requested, 354 allocated, lot#5)
 */
@Entity
@Table(
    name = "allotment_lots",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_allotment_investment",
            columnNames = {"allotment_id", "investment_id"}
        )
    },
    indexes = {
        @Index(name = "idx_allot_lot_allotment_id", columnList = "allotment_id"),
        @Index(name = "idx_allot_lot_investment_id", columnList = "investment_id"),
        @Index(name = "idx_allot_lot_lot_number", columnList = "lot_number")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllotmentLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allotment_id", nullable = false)
    private Allotment allotment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investment_id", nullable = false)
    private Investment investment;

    @Column(nullable = false)
    private Long sharesRequested;  // Original request

    @Column(nullable = false)
    private Long sharesAllocated;  // Allocated in this lot

    @Column(nullable = false)
    private Long lotNumber;  // Random lot sequence number (for fair ordering)

    @Column(precision = 10, scale = 4)
    private BigDecimal allocationPercentage;  // (allocated / requested) * 100

    @Column(nullable = false)
    private Long randomSeed;  // Seed used for audit trail

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
