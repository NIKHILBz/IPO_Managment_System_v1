package com.ipo.entity.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ipos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private BigDecimal pricePerShare;

    @Column(nullable = false)
    private Long totalSharesOffered;

    @Column(nullable = false)
    private BigDecimal totalFundsToRaise;

    @Column(nullable = false)
    private LocalDateTime launchDate;

    @Column(nullable = false)
    private LocalDateTime closingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IPOStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = IPOStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
