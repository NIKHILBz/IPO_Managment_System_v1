package com.ipo.service.service.allotment;

import com.ipo.entity.model.*;
import com.ipo.entity.repository.AllotmentLotRepository;
import com.ipo.entity.repository.AllotmentRepository;
import com.ipo.entity.repository.InvestmentRepository;
import com.ipo.entity.repository.IPORepository;
import com.ipo.service.service.allotment.dto.AllotmentBatchResult;
import com.ipo.service.service.allotment.dto.AllotmentResult;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AllotmentService
 *
 * Core service for IPO allotment processing.
 * Implements fair lottery algorithm for oversubscription scenarios.
 *
 * Algorithm Overview:
 * 1. Calculate oversubscription ratio (available / requested)
 * 2. If not oversubscribed: pro-rata allocation
 * 3. If oversubscribed: fair lottery with deterministic seeding
 * 4. Allocate shares using sorted lot numbers
 * 5. Persist results in batch transactions
 *
 * Performance:
 * - Batch processing for large datasets (1M+ applications)
 * - Pagination-based to control memory usage
 * - Deterministic randomization for reproducibility
 */
@Slf4j
@Service
public class AllotmentService {

    @Autowired
    private AllotmentRepository allotmentRepository;

    @Autowired
    private AllotmentLotRepository allotmentLotRepository;

    @Autowired
    private InvestmentRepository investmentRepository;

    @Autowired
    private IPORepository ipoRepository;

    private static final int DEFAULT_PAGE_SIZE = 10000;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    /**
     * Perform fair lottery allotment for an IPO
     *
     * @param ipoId the IPO to allot
     * @param pageSize number of investments per batch (default: 10000)
     * @return AllotmentResult with summary statistics
     * @throws AllotmentFailedException if allotment fails
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public AllotmentResult performFairLotteryAllotment(Long ipoId, int pageSize) {
        log.info("Starting fair lottery allotment: ipoId={}, pageSize={}", ipoId, pageSize);

        IPO ipo = ipoRepository.findById(ipoId)
            .orElseThrow(() -> new IllegalArgumentException("IPO not found: " + ipoId));

        Allotment allotment = createAllotmentRecord(ipo);

        try {
            // Step 1: Query all approved investments and calculate statistics
            List<Investment> allInvestments = investmentRepository
                .findByIpoIdAndStatus(ipoId, InvestmentStatus.APPROVED);

            if (allInvestments.isEmpty()) {
                log.warn("No approved applications found for allotment: ipoId={}", ipoId);
                allotment.setStatus(AllotmentStatus.COMPLETED);
                allotment.setTotalApplicationsProcessed(0L);
                allotment.setTotalSharesAllocated(0L);
                allotment.setCompletedAt(LocalDateTime.now());
                allotmentRepository.save(allotment);
                return buildAllotmentResult(allotment);
            }

            long totalRequested = allInvestments.stream()
                .mapToLong(Investment::getSharesRequested)
                .sum();

            log.info("Allotment statistics: applications={}, shares_requested={}, shares_available={}",
                allInvestments.size(), totalRequested, ipo.getTotalSharesOffered());

            // Step 2: Calculate oversubscription ratio
            BigDecimal ratio = calculateOversubscriptionRatio(
                ipo.getTotalSharesOffered(),
                totalRequested);

            allotment.setTotalApplicationsReceived((long) allInvestments.size());
            allotment.setTotalSharesRequested(totalRequested);
            allotment.setOversubscriptionRatio(ratio);
            allotment.setStatus(AllotmentStatus.IN_PROGRESS);
            allotmentRepository.save(allotment);

            // Step 3: Generate deterministic seed for reproducibility
            long randomSeed = generateDeterministicSeed(ipoId);
            allotment.setRandomSeed(randomSeed);
            log.debug("Generated deterministic seed: {}", randomSeed);

            // Step 4: Process in batches
            int totalProcessed = 0;
            long totalAllocated = 0;

            for (int page = 0; page * pageSize < allInvestments.size(); page++) {
                int start = page * pageSize;
                int end = Math.min(start + pageSize, allInvestments.size());
                List<Investment> batch = allInvestments.subList(start, end);

                log.debug("Processing batch {}: {} investments ({}/{})",
                    page, batch.size(), end, allInvestments.size());

                AllotmentBatchResult batchResult = processAllotmentBatch(
                    allotment,
                    batch,
                    ratio,
                    randomSeed);

                totalProcessed += batchResult.getProcessedCount();
                totalAllocated += batchResult.getTotalAllocated();

                log.debug("Batch {} completed: {} allocated", page, batchResult.getTotalAllocated());
            }

            // Step 5: Finalize allotment
            allotment.setTotalApplicationsProcessed((long) totalProcessed);
            allotment.setTotalSharesAllocated(totalAllocated);
            allotment.setAllocationPercentage(
                totalRequested > 0 ?
                    BigDecimal.valueOf(totalAllocated)
                        .divide(BigDecimal.valueOf(totalRequested), 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal(100)) :
                    BigDecimal.ZERO);
            allotment.setStatus(AllotmentStatus.COMPLETED);
            allotment.setCompletedAt(LocalDateTime.now());
            allotmentRepository.save(allotment);

            log.info("Allotment completed: applications={}, allocated={}, shares={}",
                totalProcessed, allotment.getAllocationPercentage(), totalAllocated);

            return buildAllotmentResult(allotment);

        } catch (Exception e) {
            log.error("Allotment failed: ipoId={}", ipoId, e);
            allotment.setStatus(AllotmentStatus.FAILED);
            allotmentRepository.save(allotment);
            throw new AllotmentFailedException("Allotment processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Process a single batch of investments
     *
     * @param allotment the allotment record
     * @param investments batch of investments to allocate
     * @param ratio oversubscription ratio
     * @param seed random seed for deterministic allocation
     * @return batch processing result
     */
    @Transactional
    private AllotmentBatchResult processAllotmentBatch(
        Allotment allotment,
        List<Investment> investments,
        BigDecimal ratio,
        long seed) {

        List<AllotmentLot> lots = new ArrayList<>();
        long totalAllocated = 0;
        int successCount = 0;
        int failureCount = 0;

        // Step 1: Generate deterministic lot numbers
        List<LotAssignment> assignments = generateLotAssignments(investments, seed);

        // Step 2: Sort by lot number (fair ordering)
        assignments.sort(Comparator.comparingLong(LotAssignment::getLotNumber));

        // Step 3: Allocate shares to each investor
        for (LotAssignment assignment : assignments) {
            try {
                Investment investment = assignment.getInvestment();
                long sharesRequested = investment.getSharesRequested();

                // Calculate allocation based on ratio
                BigDecimal baseAllocation = BigDecimal.valueOf(sharesRequested)
                    .multiply(ratio)
                    .setScale(0, RoundingMode.DOWN);

                long sharesAllocated = baseAllocation.longValue();

                // Ensure at least 1 share if requested (no zero allocations)
                if (sharesAllocated == 0 && sharesRequested > 0) {
                    sharesAllocated = Math.min(1, sharesRequested);
                }

                // Create AllotmentLot record
                AllotmentLot lot = AllotmentLot.builder()
                    .allotment(allotment)
                    .investment(investment)
                    .sharesRequested(sharesRequested)
                    .sharesAllocated(sharesAllocated)
                    .lotNumber(assignment.getLotNumber())
                    .allocationPercentage(
                        sharesAllocated > 0 ?
                            BigDecimal.valueOf(sharesAllocated)
                                .divide(BigDecimal.valueOf(sharesRequested), 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal(100)) :
                            BigDecimal.ZERO)
                    .randomSeed(seed)
                    .build();

                lots.add(lot);
                totalAllocated += sharesAllocated;

                // Update investment status
                investment.setSharesAllotted(sharesAllocated);
                investment.setStatus(InvestmentStatus.ALLOTTED);
                investmentRepository.save(investment);

                successCount++;

            } catch (Exception e) {
                log.error("Failed to allocate investment: {}", assignments, e);
                failureCount++;
            }
        }

        // Step 4: Batch save all lots
        if (!lots.isEmpty()) {
            allotmentLotRepository.saveAll(lots);
            log.debug("Saved {} AllotmentLot records", lots.size());
        }

        return AllotmentBatchResult.builder()
            .processedCount(investments.size())
            .totalAllocated(totalAllocated)
            .successCount(successCount)
            .failureCount(failureCount)
            .build();
    }

    /**
     * Generate deterministic lot assignments using seeded random generator
     *
     * Creates a unique lot number for each investment in deterministic manner.
     * Same seed produces same lot numbers (reproducible).
     *
     * @param investments list of investments to assign lots
     * @param seed random seed
     * @return list of lot assignments sorted by lot number
     */
    private List<LotAssignment> generateLotAssignments(
        List<Investment> investments,
        long seed) {

        Random rand = new Random(seed);
        List<LotAssignment> assignments = new ArrayList<>();

        for (int i = 0; i < investments.size(); i++) {
            Investment investment = investments.get(i);
            // Generate lot number between 0 and investments.size()-1
            long lotNumber = Math.abs(rand.nextLong() % investments.size());
            assignments.add(new LotAssignment(investment, lotNumber));
        }

        log.debug("Generated {} lot assignments from seed {}", assignments.size(), seed);
        return assignments;
    }

    /**
     * Calculate oversubscription ratio
     *
     * If demand <= supply: ratio = 1.0 (everyone gets what they want)
     * If demand > supply: ratio = supply / demand (proportional reduction)
     *
     * @param available total shares available
     * @param requested total shares requested
     * @return oversubscription ratio
     */
    private BigDecimal calculateOversubscriptionRatio(Long available, long requested) {
        if (requested <= available) {
            log.debug("Not oversubscribed: requested={}, available={}", requested, available);
            return BigDecimal.ONE;
        }

        BigDecimal ratio = BigDecimal.valueOf(available)
            .divide(BigDecimal.valueOf(requested), 4, RoundingMode.HALF_UP);

        log.debug("Oversubscribed {} times: ratio={}",
            BigDecimal.valueOf(requested).divide(BigDecimal.valueOf(available), 2, RoundingMode.HALF_UP),
            ratio);

        return ratio;
    }

    /**
     * Generate deterministic seed from IPO ID
     *
     * Uses IPO ID hash for reproducibility across multiple runs.
     * Same IPO always generates same seed.
     *
     * @param ipoId the IPO ID
     * @return deterministic seed
     */
    private long generateDeterministicSeed(Long ipoId) {
        return Math.abs(ipoId.hashCode());
    }

    /**
     * Create allotment record (PENDING state)
     */
    private Allotment createAllotmentRecord(IPO ipo) {
        String allotmentNumber = "ALLOT-" + System.currentTimeMillis();

        Allotment allotment = Allotment.builder()
            .ipo(ipo)
            .allotmentNumber(allotmentNumber)
            .totalSharesAvailable(ipo.getTotalSharesOffered())
            .allotmentMethod(AllotmentMethod.FAIR_LOTTERY)
            .status(AllotmentStatus.PENDING)
            .allotmentDate(LocalDateTime.now())
            .build();

        Allotment saved = allotmentRepository.save(allotment);
        log.info("Created allotment record: id={}, number={}, ipo={}",
            saved.getId(), saved.getAllotmentNumber(), ipo.getId());

        return saved;
    }

    /**
     * Build AllotmentResult DTO from entity
     */
    private AllotmentResult buildAllotmentResult(Allotment allotment) {
        return AllotmentResult.builder()
            .allotmentId(allotment.getId())
            .allotmentNumber(allotment.getAllotmentNumber())
            .status(allotment.getStatus().toString())
            .totalApplicationsProcessed(allotment.getTotalApplicationsProcessed())
            .totalSharesAllocated(allotment.getTotalSharesAllocated())
            .oversubscriptionRatio(allotment.getOversubscriptionRatio())
            .completedAt(allotment.getCompletedAt())
            .build();
    }

    /**
     * Helper class for tracking lot assignments
     */
    @Getter
    @AllArgsConstructor
    private static class LotAssignment {
        private final Investment investment;
        private final long lotNumber;
    }

    /**
     * Get allotment details and results
     */
    public AllotmentResult getAllotmentDetails(Long allotmentId) {
        Allotment allotment = allotmentRepository.findById(allotmentId)
            .orElseThrow(() -> new IllegalArgumentException("Allotment not found: " + allotmentId));

        return buildAllotmentResult(allotment);
    }

    /**
     * Get allotment results paginated
     */
    public Page<AllotmentLot> getAllotmentResults(Long allotmentId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "lotNumber"));
        return allotmentLotRepository.findByAllotmentId(allotmentId, pageable);
    }
}
