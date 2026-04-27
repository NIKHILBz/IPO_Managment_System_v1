package com.ipo.entity.repository;

import com.ipo.entity.model.ApplicationForm;
import com.ipo.entity.model.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationFormRepository extends JpaRepository<ApplicationForm, Long> {

    /**
     * Find by application number
     */
    Optional<ApplicationForm> findByApplicationNumber(String applicationNumber);

    /**
     * Find all applications for an IPO
     */
    List<ApplicationForm> findByIpoId(Long ipoId);

    /**
     * Find all applications by investor
     */
    List<ApplicationForm> findByInvestorId(Long investorId);

    /**
     * Find applications by status
     */
    List<ApplicationForm> findByStatus(ApplicationStatus status);

    /**
     * Check if investor has already applied for IPO (duplicate prevention)
     */
    @Query("SELECT COUNT(a) > 0 FROM ApplicationForm a WHERE a.ipo.id = :ipoId AND a.investor.id = :investorId")
    boolean existsByIpoIdAndInvestorId(@Param("ipoId") Long ipoId, @Param("investorId") Long investorId);

    /**
     * Find existing application for investor and IPO with PESSIMISTIC_WRITE lock
     * Used during application submission to prevent race conditions
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ApplicationForm a WHERE a.ipo.id = :ipoId AND a.investor.id = :investorId")
    Optional<ApplicationForm> findByIpoIdAndInvestorIdWithLock(@Param("ipoId") Long ipoId, @Param("investorId") Long investorId);

    /**
     * Find application with optimistic locking support (version-based)
     */
    @Query("SELECT a FROM ApplicationForm a WHERE a.id = :id")
    Optional<ApplicationForm> findByIdForUpdate(@Param("id") Long id);
}

