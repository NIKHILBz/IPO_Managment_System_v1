package com.ipo.service.service;

import com.ipo.entity.model.Investment;
import com.ipo.entity.model.InvestmentStatus;
import com.ipo.entity.repository.InvestmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Service
@Transactional
public class ConcurrentOperationService {

    @Autowired
    private InvestmentRepository investmentRepository;

    private final ConcurrentHashMap<Long, ReadWriteLock> investmentLocks = new ConcurrentHashMap<>();

    /**
     * Get or create a lock for an investment
     */
    private ReadWriteLock getInvestmentLock(Long investmentId) {
        return investmentLocks.computeIfAbsent(investmentId, k -> new ReentrantReadWriteLock());
    }

    /**
     * Process investment allocation with concurrency control
     */
    public Investment processInvestmentAllocation(Long investmentId, Long allocatedShares) {
        ReadWriteLock lock = getInvestmentLock(investmentId);
        lock.writeLock().lock();
        try {
            log.info("Processing investment allocation with concurrency control: investmentId={}, allocatedShares={}",
                    investmentId, allocatedShares);

            Investment investment = investmentRepository.findById(investmentId)
                    .orElseThrow(() -> new RuntimeException("Investment not found with id: " + investmentId));

            // Prevent double allocation
            if (investment.getStatus() == InvestmentStatus.ALLOTTED) {
                throw new RuntimeException("Investment already allotted");
            }

            // Validate allocation
            if (allocatedShares > investment.getSharesRequested()) {
                throw new RuntimeException("Allocated shares cannot exceed requested shares");
            }

            investment.setSharesAllotted(allocatedShares);
            investment.setStatus(InvestmentStatus.ALLOTTED);

            log.info("Investment allocation completed: investmentId={}, allocatedShares={}", investmentId, allocatedShares);
            return investmentRepository.save(investment);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Safe concurrent fund transfer simulation
     */
    public Investment transferFunds(Long investmentId, BigDecimal amount) {
        ReadWriteLock lock = getInvestmentLock(investmentId);
        lock.writeLock().lock();
        try {
            log.info("Processing fund transfer with concurrency control: investmentId={}, amount={}",
                    investmentId, amount);

            Investment investment = investmentRepository.findById(investmentId)
                    .orElseThrow(() -> new RuntimeException("Investment not found with id: " + investmentId));

            if (investment.getStatus() != InvestmentStatus.APPROVED) {
                throw new RuntimeException("Investment must be approved before fund transfer");
            }

            // Simulate fund transfer
            investment.setAmountInvested(amount);
            investment.setStatus(InvestmentStatus.COMPLETED);

            log.info("Fund transfer completed: investmentId={}, amount={}", investmentId, amount);
            return investmentRepository.save(investment);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Read investment status with read lock
     */
    public InvestmentStatus readInvestmentStatus(Long investmentId) {
        ReadWriteLock lock = getInvestmentLock(investmentId);
        lock.readLock().lock();
        try {
            Investment investment = investmentRepository.findById(investmentId)
                    .orElseThrow(() -> new RuntimeException("Investment not found with id: " + investmentId));
            return investment.getStatus();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Batch process investments with concurrency control
     */
    @Transactional
    public void batchApproveInvestments(Long[] investmentIds) {
        log.info("Batch processing investments with concurrency control: count={}", investmentIds.length);

        for (Long investmentId : investmentIds) {
            ReadWriteLock lock = getInvestmentLock(investmentId);
            lock.writeLock().lock();
            try {
                Investment investment = investmentRepository.findById(investmentId)
                        .orElseThrow(() -> new RuntimeException("Investment not found with id: " + investmentId));

                if (investment.getStatus() == InvestmentStatus.PENDING) {
                    investment.setStatus(InvestmentStatus.APPROVED);
                    investmentRepository.save(investment);
                    log.debug("Investment approved: investmentId={}", investmentId);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
        log.info("Batch processing completed");
    }

    /**
     * Release lock for an investment (cleanup)
     */
    public void releaseLock(Long investmentId) {
        investmentLocks.remove(investmentId);
        log.debug("Lock released for investment: investmentId={}", investmentId);
    }

    /**
     * Get current lock count (for monitoring)
     */
    public int getActiveLockCount() {
        return investmentLocks.size();
    }

    /**
     * Clear all locks (use with caution)
     */
    public void clearAllLocks() {
        log.warn("Clearing all investment locks");
        investmentLocks.clear();
    }
}
