package com.ipo.entity.repository;

import com.ipo.entity.model.Investment;
import com.ipo.entity.model.InvestmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvestmentRepository extends JpaRepository<Investment, Long> {

    /**
     * Find investments by IPO
     */
    List<Investment> findByIpoId(Long ipoId);

    /**
     * Find investments by investor
     */
    List<Investment> findByInvestorId(Long investorId);

    /**
     * Find investments by status
     */
    List<Investment> findByStatus(InvestmentStatus status);

    /**
     * Find investments by IPO and status (for allotment processing)
     */
    List<Investment> findByIpoIdAndStatus(Long ipoId, InvestmentStatus status);

    /**
     * Get total shares requested for an IPO (for oversubscription ratio calculation)
     */
    @Query("SELECT SUM(i.sharesRequested) FROM Investment i WHERE i.ipo.id = :ipoId AND i.status = :status")
    Long getTotalSharesRequested(@Param("ipoId") Long ipoId, @Param("status") InvestmentStatus status);

    /**
     * Count investments by IPO and status
     */
    long countByIpoIdAndStatus(Long ipoId, InvestmentStatus status);
}
