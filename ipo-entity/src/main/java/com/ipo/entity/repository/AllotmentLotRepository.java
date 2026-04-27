package com.ipo.entity.repository;

import com.ipo.entity.model.AllotmentLot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;

/**
 * AllotmentLotRepository
 *
 * Data access layer for AllotmentLot entities.
 * Provides query methods for batch processing and statistics.
 */
@Repository
public interface AllotmentLotRepository extends JpaRepository<AllotmentLot, Long> {

    /**
     * Find all lots for an allotment (paginated)
     * Used for batch processing and result retrieval
     */
    Page<AllotmentLot> findByAllotmentId(Long allotmentId, Pageable pageable);

    /**
     * Count total lots for an allotment
     */
    long countByAllotmentId(Long allotmentId);

    /**
     * Get total shares allocated in an allotment
     */
    @Query("SELECT SUM(al.sharesAllocated) FROM AllotmentLot al WHERE al.allotment.id = :allotmentId")
    Long getTotalAllocated(@Param("allotmentId") Long allotmentId);

    /**
     * Find lots for an allotment with PESSIMISTIC_WRITE lock
     * Used during batch processing to prevent concurrent modifications
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Page<AllotmentLot> findByAllotmentIdForUpdate(Long allotmentId, Pageable pageable);

    /**
     * Check if an investment was already allocated in this allotment
     */
    @Query("SELECT COUNT(al) > 0 FROM AllotmentLot al WHERE al.allotment.id = :allotmentId AND al.investment.id = :investmentId")
    boolean existsByAllotmentIdAndInvestmentId(@Param("allotmentId") Long allotmentId, @Param("investmentId") Long investmentId);
}
