package com.ipo.entity.repository;

import com.ipo.entity.model.Allotment;
import com.ipo.entity.model.AllotmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * AllotmentRepository
 *
 * Data access layer for Allotment entities.
 * Provides query methods for allotment retrieval and batch processing.
 */
@Repository
public interface AllotmentRepository extends JpaRepository<Allotment, Long> {

    /**
     * Find allotment by unique allotment number
     */
    Optional<Allotment> findByAllotmentNumber(String allotmentNumber);

    /**
     * Find the latest (most recent) allotment for an IPO
     */
    @Query("SELECT a FROM Allotment a WHERE a.ipo.id = :ipoId ORDER BY a.allotmentDate DESC LIMIT 1")
    Optional<Allotment> findLatestByIpoId(@Param("ipoId") Long ipoId);

    /**
     * Find all allotments for an IPO, ordered by date descending
     */
    List<Allotment> findByIpoIdOrderByAllotmentDateDesc(Long ipoId);

    /**
     * Count allotments for an IPO with specific status
     */
    long countByIpoIdAndStatus(Long ipoId, AllotmentStatus status);

    /**
     * Get total allocated shares for an IPO (across all allotments)
     */
    @Query("SELECT SUM(a.totalSharesAllocated) FROM Allotment a WHERE a.ipo.id = :ipoId")
    Long getTotalAllocatedShares(@Param("ipoId") Long ipoId);

    /**
     * Find the latest allotment with PESSIMISTIC_WRITE lock
     * Used to prevent concurrent allotment runs for same IPO
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Allotment a WHERE a.ipo.id = :ipoId ORDER BY a.allotmentDate DESC LIMIT 1")
    Optional<Allotment> findLatestByIpoIdWithLock(@Param("ipoId") Long ipoId);

    /**
     * Find all allotments with PENDING or IN_PROGRESS status (for recovery)
     */
    List<Allotment> findByStatusIn(List<AllotmentStatus> statuses);
}
