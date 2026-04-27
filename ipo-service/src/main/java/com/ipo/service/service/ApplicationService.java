package com.ipo.service.service;

import com.ipo.entity.model.ApplicationForm;
import com.ipo.entity.model.ApplicationStatus;
import com.ipo.entity.repository.ApplicationFormRepository;
import com.ipo.service.event.dto.ApplicationEventDto;
import com.ipo.service.kafka.KafkaProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application Service with Advanced Concurrency Control
 *
 * Handles IPO application submissions with multiple layers of duplicate prevention:
 * 1. Distributed Locking (Redis) - prevents concurrent submissions from same user
 * 2. Pessimistic Locking - row-level lock during submission
 * 3. Optimistic Locking - version column for concurrent update detection
 * 4. Unique Constraint - database-level duplicate prevention
 *
 * Locking Strategies Explained:
 * ================================
 *
 * OPTIMISTIC LOCKING:
 * - Uses @Version field (version column in DB)
 * - No database locks held
 * - Good for: Read-heavy, infrequent conflicts
 * - Conflict Resolution: Retry on ObjectOptimisticLockingFailureException
 * - Cost: Lower - allows concurrent reads; Higher - retry cost on conflicts
 * Example: findByIdForUpdate() uses version to detect concurrent changes
 *
 * PESSIMISTIC LOCKING:
 * - Locks rows immediately for duration of transaction
 * - Prevents other transactions from accessing locked rows
 * - Good for: Write-heavy, must prevent conflicts
 * - Types:
 *   - PESSIMISTIC_READ: Shared lock (blocks writes, allows reads)
 *   - PESSIMISTIC_WRITE: Exclusive lock (blocks reads and writes)
 * - Cost: Higher - blocks concurrent access; Lower - no retries
 * Example: findByIpoIdAndInvestorIdWithLock() uses PESSIMISTIC_WRITE
 *
 * DISTRIBUTED LOCKING (Redis):
 * - Application-level lock across multiple instances
 * - Prevents concurrent execution in distributed systems
 * - Good for: Multi-instance deployments, critical sections
 * - Implementation: SET NX with TTL + token validation
 * - Cost: Network call to Redis; Automatic expiration prevents deadlocks
 * Example: Lock during submitApplication() to prevent duplicate submissions
 *
 * UNIQUE CONSTRAINT:
 * - Last line of defense: DB enforces uniqueness
 * - Raises DataIntegrityViolationException on violation
 * - Good for: Final consistency guarantee
 * Example: (ipo_id, investor_id) unique constraint in ApplicationForm table
 */
@Slf4j
@Service
public class ApplicationService {

    @Autowired
    private ApplicationFormRepository applicationFormRepository;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    @Autowired
    private DistributedLockService distributedLockService;

    private static final String APPLICATION_LOCK_KEY_PREFIX = "app:ipo:";
    private static final int MAX_RETRY_ATTEMPTS = 3;

    // ==================== READ OPERATIONS ====================

    @Cacheable(value = "applications", key = "#id")
    public Optional<ApplicationForm> getApplicationById(Long id) {
        log.debug("Fetching application with id: {}", id);
        return applicationFormRepository.findById(id);
    }

    @Cacheable(value = "applications", key = "#applicationNumber")
    public Optional<ApplicationForm> getApplicationByNumber(String applicationNumber) {
        log.debug("Fetching application with number: {}", applicationNumber);
        return applicationFormRepository.findByApplicationNumber(applicationNumber);
    }

    public List<ApplicationForm> getApplicationsByIPOId(Long ipoId) {
        log.debug("Fetching applications for IPO: {}", ipoId);
        return applicationFormRepository.findByIpoId(ipoId);
    }

    public List<ApplicationForm> getApplicationsByInvestorId(Long investorId) {
        log.debug("Fetching applications for investor: {}", investorId);
        return applicationFormRepository.findByInvestorId(investorId);
    }

    public List<ApplicationForm> getApplicationsByStatus(ApplicationStatus status) {
        log.debug("Fetching applications with status: {}", status);
        return applicationFormRepository.findByStatus(status);
    }

    public List<ApplicationForm> getAllApplications() {
        log.debug("Fetching all applications");
        return applicationFormRepository.findAll();
    }

    // ==================== WRITE OPERATIONS ====================

    /**
     * Submit application with comprehensive concurrency control
     *
     * Duplicate Prevention Strategy:
     * 1. Distributed Lock: Redis lock prevents concurrent submission from same user
     * 2. Pessimistic Lock: Row-level lock checks for existing application
     * 3. Unique Constraint: Database enforces (ipo_id, investor_id) uniqueness
     *
     * @param applicationForm application to submit
     * @return submitted application
     * @throws RuntimeException if duplicate application exists
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CacheEvict(value = "applications", allEntries = true)
    public ApplicationForm submitApplication(ApplicationForm applicationForm) {
        Long ipoId = applicationForm.getIpo().getId();
        Long investorId = applicationForm.getInvestor().getId();

        String lockKey = APPLICATION_LOCK_KEY_PREFIX + ipoId + ":investor:" + investorId;
        String lockToken = null;

        try {
            // Step 1: Acquire distributed lock (prevent concurrent submissions from same user)
            lockToken = distributedLockService.acquireLock(lockKey);
            if (lockToken == null) {
                log.warn("Failed to acquire distributed lock for duplicate application submission: ipoId={}, investorId={}",
                    ipoId, investorId);
                throw new RuntimeException("Application submission in progress. Please try again later.");
            }

            log.info("Distributed lock acquired for application submission: ipoId={}, investorId={}", ipoId, investorId);

            // Step 2: Check for existing application using pessimistic lock (prevents race conditions)
            Optional<ApplicationForm> existingApp = applicationFormRepository
                .findByIpoIdAndInvestorIdWithLock(ipoId, investorId);

            if (existingApp.isPresent()) {
                log.warn("Duplicate application detected: ipoId={}, investorId={}", ipoId, investorId);
                throw new RuntimeException("You have already applied for this IPO");
            }

            // Step 3: Generate unique application number and set status
            applicationForm.setApplicationNumber(generateApplicationNumber());
            applicationForm.setStatus(ApplicationStatus.SUBMITTED);

            // Step 4: Save application (triggers unique constraint validation)
            ApplicationForm savedApplication;
            try {
                savedApplication = applicationFormRepository.save(applicationForm);
                log.info("Application submitted successfully: applicationId={}, ipoId={}, investorId={}",
                    savedApplication.getId(), ipoId, investorId);
            } catch (DataIntegrityViolationException e) {
                log.error("Duplicate application constraint violation: ipoId={}, investorId={}", ipoId, investorId, e);
                throw new RuntimeException("Duplicate application already exists. Please check your applications.", e);
            }

            // Step 5: Send event for async processing
            ApplicationEventDto eventDto = mapToEventDto(savedApplication);
            kafkaProducerService.sendApplicationSubmittedEvent(eventDto);

            return savedApplication;

        } finally {
            // Step 6: Release distributed lock
            if (lockToken != null) {
                boolean released = distributedLockService.releaseLock(lockKey, lockToken);
                log.debug("Distributed lock release result: key={}, released={}", lockKey, released);
            }
        }
    }

    /**
     * Check if investor already applied for IPO (duplicate check)
     *
     * @param ipoId the IPO ID
     * @param investorId the investor ID
     * @return true if application exists, false otherwise
     */
    public boolean isDuplicateApplication(Long ipoId, Long investorId) {
        return applicationFormRepository.existsByIpoIdAndInvestorId(ipoId, investorId);
    }

    /**
     * Approve application with optimistic locking retry
     *
     * Uses optimistic locking (@Version) to detect concurrent updates.
     * If conflict detected, retries up to MAX_RETRY_ATTEMPTS.
     *
     * @param id application ID
     * @return approved application
     */
    @CacheEvict(value = "applications", allEntries = true)
    public ApplicationForm approveApplication(Long id) {
        log.info("Approving application with id: {}", id);

        int attempts = 0;
        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                return approveApplicationWithOptimisticLock(id);
            } catch (ObjectOptimisticLockingFailureException e) {
                attempts++;
                if (attempts >= MAX_RETRY_ATTEMPTS) {
                    log.error("Optimistic locking failure - max retries exceeded: applicationId={}", id);
                    throw new RuntimeException("Failed to approve application after " + MAX_RETRY_ATTEMPTS + " attempts", e);
                }
                log.warn("Optimistic lock conflict detected, retrying ({}/{}): applicationId={}",
                    attempts, MAX_RETRY_ATTEMPTS, id);
                try {
                    Thread.sleep(100 * attempts); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while retrying approval", ie);
                }
            }
        }
        throw new RuntimeException("Unexpected state in approveApplication");
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    private ApplicationForm approveApplicationWithOptimisticLock(Long id) {
        ApplicationForm application = applicationFormRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Application not found with id: " + id));

        application.setStatus(ApplicationStatus.APPROVED);
        application.setRejectionReason(null);

        return applicationFormRepository.save(application);
    }

    /**
     * Reject application with reason
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @CacheEvict(value = "applications", allEntries = true)
    public ApplicationForm rejectApplication(Long id, String rejectionReason) {
        log.info("Rejecting application with id: {}", id);
        ApplicationForm application = applicationFormRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Application not found with id: " + id));

        application.setStatus(ApplicationStatus.REJECTED);
        application.setRejectionReason(rejectionReason);

        return applicationFormRepository.save(application);
    }

    /**
     * Update application status
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @CacheEvict(value = "applications", allEntries = true)
    public ApplicationForm updateApplicationStatus(Long id, ApplicationStatus status) {
        log.info("Updating application status with id: {}, status: {}", id, status);
        ApplicationForm application = applicationFormRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Application not found with id: " + id));

        application.setStatus(status);
        return applicationFormRepository.save(application);
    }

    /**
     * Delete application (only if in SUBMITTED status)
     */
    @Transactional
    @CacheEvict(value = "applications", allEntries = true)
    public void deleteApplication(Long id) {
        log.info("Deleting application with id: {}", id);
        ApplicationForm application = applicationFormRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Application not found with id: " + id));

        if (application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new RuntimeException("Can only delete applications in SUBMITTED status");
        }

        applicationFormRepository.deleteById(id);
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Generate unique application number
     * Format: APP-{timestamp}-{uuid}
     */
    private String generateApplicationNumber() {
        return "APP-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Map ApplicationForm entity to event DTO
     */
    private ApplicationEventDto mapToEventDto(ApplicationForm application) {
        return ApplicationEventDto.builder()
            .applicationId(application.getId())
            .ipoId(application.getIpo().getId())
            .investorId(application.getInvestor().getId())
            .applicationNumber(application.getApplicationNumber())
            .status(application.getStatus().toString())
            .rejectionReason(application.getRejectionReason())
            .createdAt(application.getCreatedAt())
            .build();
    }
}
