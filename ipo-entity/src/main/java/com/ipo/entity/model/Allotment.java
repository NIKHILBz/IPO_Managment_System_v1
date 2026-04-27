package com.ipo.entity.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Allotment Entity
 *
 * Represents an allotment batch/run for an IPO.
 * Tracks the overall allocation process and statistics.
 *
 * Example: "IPO-1 Allotment Run - Fair Lottery"
 */
@Entity
@Table(
    name = "allotments",
    indexes = {
        @Index(name = "idx_allotment_ipo_id", columnList = "ipo_id"),
        @Index(name = "idx_allotment_status", columnList = "status"),
        @Index(name = "idx_allotment_date", columnList = "allotment_date")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Allotment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ipo_id", nullable = false)
    private IPO ipo;

    @Column(nullable = false, unique = true)
    private String allotmentNumber;  // e.g., "ALLOT-2026-001"

    @Column(nullable = false)
    private Long totalSharesAvailable;

    @Column(nullable = false)
    private Long totalApplicationsReceived;

    @Column(nullable = false)
    private Long totalApplicationsProcessed;

    @Column(nullable = false)
    private Long totalSharesRequested;

    @Column(nullable = false)
    private Long totalSharesAllocated;

    @Column(precision = 10, scale = 4)
    private BigDecimal oversubscriptionRatio;  // totalRequested / available

    @Column(precision = 10, scale = 4)
    private BigDecimal allocationPercentage;  // avg allocation %

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AllotmentMethod allotmentMethod;  // FAIR_LOTTERY, PRO_RATA

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AllotmentStatus status;  // PENDING, IN_PROGRESS, COMPLETED, FAILED

    @Column
    private Long randomSeed;  // Seed used for deterministic lottery (for reproducibility)

    @Column(nullable = false)
    private LocalDateTime allotmentDate;  // When allotment was initiated

    @Column
    private LocalDateTime completedAt;  // When allotment finished

    @Version
    @Column(nullable = false)
    private Long version;  // Optimistic locking

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = AllotmentStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
