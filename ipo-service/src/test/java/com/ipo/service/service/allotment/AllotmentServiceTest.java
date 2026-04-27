// package com.ipo.service.service.allotment;

// import com.ipo.entity.model.*;
// import com.ipo.entity.repository.AllotmentLotRepository;
// import com.ipo.entity.repository.AllotmentRepository;
// import com.ipo.entity.repository.InvestmentRepository;
// import com.ipo.entity.repository.IPORepository;
// import com.ipo.service.service.allotment.dto.AllotmentResult;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.InjectMocks;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;

// import java.math.BigDecimal;
// import java.time.LocalDateTime;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.Optional;

// import static org.junit.jupiter.api.Assertions.*;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.eq;
// import static org.mockito.Mockito.*;

// /**
//  * AllotmentService Test Suite
//  *
//  * Tests for fair lottery allocation algorithm and batch processing.
//  * Covers: normal allocation, oversubscription, randomization, error handling.
//  */
// @ExtendWith(MockitoExtension.class)
// @DisplayName("Allotment Service Tests")
// public class AllotmentServiceTest {

//     @Mock
//     private AllotmentRepository allotmentRepository;

//     @Mock
//     private AllotmentLotRepository allotmentLotRepository;

//     @Mock
//     private InvestmentRepository investmentRepository;

//     @Mock
//     private IPORepository ipoRepository;

//     @InjectMocks
//     private AllotmentService allotmentService;

//     private IPO testIPO;
//     private List<Investment> testInvestments;

//     @BeforeEach
//     public void setUp() {
//         testIPO = IPO.builder()
//             .id(1L)
//             .company(Company.builder().id(1L).companyName("TechCorp").build())
//             .totalSharesOffered(1000L)
//             .pricePerShare(new BigDecimal("100.50"))
//             .status(IPOStatus.OPENED)
//             .createdAt(LocalDateTime.now())
//             .updatedAt(LocalDateTime.now())
//             .build();

//         testInvestments = new ArrayList<>();
//     }

//     // ==================== NORMAL ALLOCATION (No Oversubscription) ====================

//     @Test
//     @DisplayName("Allocate shares when request < available (no oversubscription)")
//     public void testAllocationNormal_RequestLessThanAvailable() {
//         // Setup: 1000 shares available, 500 requested
//         Investment inv1 = createInvestment(1L, 300L);
//         Investment inv2 = createInvestment(2L, 200L);
//         testInvestments.add(inv1);
//         testInvestments.add(inv2);

//         when(ipoRepository.findById(1L)).thenReturn(Optional.of(testIPO));
//         when(investmentRepository.findByIpoIdAndStatus(1L, InvestmentStatus.APPROVED))
//             .thenReturn(testInvestments);
//         when(allotmentRepository.save(any(Allotment.class)))
//             .thenAnswer(invocation -> invocation.getArgument(0));
//         when(allotmentLotRepository.saveAll(any()))
//             .thenAnswer(invocation -> invocation.getArgument(0));

//         // Execute
//         AllotmentResult result = allotmentService.performFairLotteryAllotment(1L, 10000);

//         // Verify
//         assertTrue(result.isSuccessful());
//         assertEquals(2L, result.getTotalApplicationsProcessed());
//         assertEquals(500L, result.getTotalSharesAllocated());
//         assertEquals(BigDecimal.ONE, result.getOversubscriptionRatio());

//         verify(allotmentRepository, times(2)).save(any(Allotment.class));
//         verify(allotmentLotRepository, times(1)).saveAll(any());
//     }

//     // ==================== OVERSUBSCRIPTION (Demand > Supply) ====================

//     @Test
//     @DisplayName("Fair lottery allocation when 2x oversubscribed")
//     public void testAllocationFairLottery_Oversubscribed2x() {
//         // Setup: 1000 shares, 2000 requested (2x oversubscribed)
//         Investment inv1 = createInvestment(1L, 600L);
//         Investment inv2 = createInvestment(2L, 400L);
//         Investment inv3 = createInvestment(3L, 1000L);
//         testInvestments.addAll(List.of(inv1, inv2, inv3));

//         when(ipoRepository.findById(1L)).thenReturn(Optional.of(testIPO));
//         when(investmentRepository.findByIpoIdAndStatus(1L, InvestmentStatus.APPROVED))
//             .thenReturn(testInvestments);
//         when(allotmentRepository.save(any(Allotment.class)))
//             .thenAnswer(invocation -> invocation.getArgument(0));
//         when(allotmentLotRepository.saveAll(any()))
//             .thenAnswer(invocation -> invocation.getArgument(0));

//         // Execute
//         AllotmentResult result = allotmentService.performFairLotteryAllotment(1L, 10000);

//         // Verify: Everyone should get ~50% (2000 / 2 = 1000 available)
//         assertTrue(result.isSuccessful());
//         assertEquals(3L, result.getTotalApplicationsProcessed());
//         assertEquals(1000L, result.getTotalSharesAllocated());

//         // Oversubscription ratio should be 0.5
//         assertEquals(new BigDecimal("0.5000"), result.getOversubscriptionRatio());

//         verify(allotmentLotRepository, times(1)).saveAll(any());
//     }

//     @Test
//     @DisplayName("Fair lottery allocation when 10x oversubscribed")
//     public void testAllocationFairLottery_HeavyOversubscription() {
//         // Setup: 1000 shares, 10000 requested (10x oversubscribed)
//         for (int i = 1; i <= 10; i++) {
//             testInvestments.add(createInvestment((long) i, 1000L));
//         }

//         when(ipoRepository.findById(1L)).thenReturn(Optional.of(testIPO));
//         when(investmentRepository.findByIpoIdAndStatus(1L, InvestmentStatus.APPROVED))
//             .thenReturn(testInvestments);
//         when(allotmentRepository.save(any(Allotment.class)))
//             .thenAnswer(invocation -> invocation.getArgument(0));
//         when(allotmentLotRepository.saveAll(any()))
//             .thenAnswer(invocation -> invocation.getArgument(0));

//         // Execute
//         AllotmentResult result = allotmentService.performFairLotteryAllotment(1L, 10000);

//         // Verify: Everyone should get ~10% (1000 / 10000 = 0.1)
//         assertTrue(result.isSuccessful());
//         assertEquals(10L, result.getTotalApplicationsProcessed());
//         assertEquals(1000L, result.getTotalSharesAllocated());

//         // Oversubscription ratio should be 0.1
//         assertEquals(new BigDecimal("0.1000"), result.getOversubscriptionRatio());
//     }

//     // ==================== RANDOMIZATION & REPRODUCIBILITY ====================

//     @Test
//     @DisplayName("Fair lottery produces deterministic results with same seed")
//     public void testDeterministicRandomization_ReproducibleResults() {
//         // Run allotment twice with same investments
//         for (int i = 0; i < 5; i++) {
//             testInvestments.add(createInvestment((long) i, 100L));
//         }

//         when(ipoRepository.findById(1L)).thenReturn(Optional.of(testIPO));
//         when(investmentRepository.findByIpoIdAndStatus(1L, InvestmentStatus.APPROVED))
//             .thenReturn(testInvestments);
//         when(allotmentRepository.save(any(Allotment.class)))
//             .thenAnswer(invocation -> invocation.getArgument(0));
//         when(allotmentLotRepository.saveAll(any()))
//             .thenAnswer(invocation -> invocation.getArgument(0));

//         // Execute first allotment
//         AllotmentResult result1 = allotmentService.performFairLotteryAllotment(1L, 10000);

//         // Execute second allotment
//         AllotmentResult result2 = allotmentService.performFairLotteryAllotment(1L, 10000);

//         // Verify: Results should be identical (same seed produces same allocation)
//         assertEquals(result1.getTotalSharesAllocated(), result2.getTotalSharesAllocated());
//         assertEquals(result1.getOversubscriptionRatio(), result2.getOversubscriptionRatio());
//     }

//     // ==================== EDGE CASES ====================

//     @Test
//     @DisplayName("Handle single application allocation")
//     public void testAllocationEdgeCase_SingleApplication() {
//         testInvestments.add(createInvestment(1L, 500L));

//         when(ipoRepository.findById(1L)).thenReturn(Optional.of(testIPO));
//         when(investmentRepository.findByIpoIdAndStatus(1L, InvestmentStatus.APPROVED))
//             .thenReturn(testInvestments);
//         when(allotmentRepository.save(any(Allotment.class)))
//             .thenAnswer(invocation -> invocation.getArgument(0));
//         when(allotmentLotRepository.saveAll(any()))
//             .thenAnswer(invocation -> invocation.getArgument(0));

//         // Execute
//         AllotmentResult result = allotmentService.performFairLotteryAllotment(1L, 10000);

//         // Verify: Single investor gets their full request
//         assertTrue(result.isSuccessful());
//         assertEquals(1L, result.getTotalApplicationsProcessed());
//         assertEquals(500L, result.getTotalSharesAllocated());
//     }

//     @Test
//     @DisplayName("Handle no applications for allotment")
//     public void testAllocationEdgeCase_NoApplications() {
//         when(ipoRepository.findById(1L)).thenReturn(Optional.of(testIPO));
//         when(investmentRepository.findByIpoIdAndStatus(1L, InvestmentStatus.APPROVED))
//             .thenReturn(new ArrayList<>());
//         when(allotmentRepository.save(any(Allotment.class)))
//             .thenAnswer(invocation -> invocation.getArgument(0));

//         // Execute
//         AllotmentResult result = allotmentService.performFairLotteryAllotment(1L, 10000);

//         // Verify: Completes gracefully with zero allocations
//         assertTrue(result.isSuccessful());
//         assertEquals(0L, result.getTotalApplicationsProcessed());
//         assertEquals(0L, result.getTotalSharesAllocated());
//     }

//     @Test
//     @DisplayName("Handle allocation when shares = 0")
//     public void testAllocationEdgeCase_ZeroShares() {
//         IPO zeroShareIPO = IPO.builder()
//             .id(2L)
//             .company(Company.builder().id(1L).build())
//             .totalSharesOffered(0L)
//             .status(IPOStatus.OPENED)
//             .build();

//         testInvestments.add(createInvestment(1L, 100L));

//         when(ipoRepository.findById(2L)).thenReturn(Optional.of(zeroShareIPO));
//         when(investmentRepository.findByIpoIdAndStatus(2L, InvestmentStatus.APPROVED))
//             .thenReturn(testInvestments);
//         when(allotmentRepository.save(any(Allotment.class)))
//             .thenAnswer(invocation -> invocation.getArgument(0));
//         when(allotmentLotRepository.saveAll(any()))
//             .thenAnswer(invocation -> invocation.getArgument(0));

//         // Execute
//         AllotmentResult result = allotmentService.performFairLotteryAllotment(2L, 10000);

//         // Verify: Zero shares should be allocated
//         assertTrue(result.isSuccessful());
//         assertEquals(0L, result.getTotalSharesAllocated());
//     }

//     // ==================== ERROR HANDLING ====================

//     @Test
//     @DisplayName("Throw exception when IPO not found")
//     public void testErrorHandling_IPONotFound() {
//         when(ipoRepository.findById(999L)).thenReturn(Optional.empty());

//         // Execute & Verify
//         assertThrows(IllegalArgumentException.class, () -> {
//             allotmentService.performFairLotteryAllotment(999L, 10000);
//         });
//     }

//     @Test
//     @DisplayName("Handle AllotmentFailedException on processing error")
//     public void testErrorHandling_ProcessingError() {
//         testInvestments.add(createInvestment(1L, 500L));

//         when(ipoRepository.findById(1L)).thenReturn(Optional.of(testIPO));
//         when(investmentRepository.findByIpoIdAndStatus(1L, InvestmentStatus.APPROVED))
//             .thenThrow(new RuntimeException("Database error"));
//         when(allotmentRepository.save(any(Allotment.class)))
//             .thenAnswer(invocation -> invocation.getArgument(0));

//         // Execute & Verify
//         assertThrows(AllotmentFailedException.class, () -> {
//             allotmentService.performFairLotteryAllotment(1L, 10000);
//         });

//         // Verify status marked as FAILED
//         verify(allotmentRepository, atLeast(1)).save(argThat(a -> a.getStatus() == AllotmentStatus.FAILED));
//     }

//     // ==================== BATCH PROCESSING ====================

//     @Test
//     @DisplayName("Process large dataset in batches")
//     public void testBatchProcessing_LargeDataset() {
//         // Create 25K applications (will be processed in 3 batches with pageSize=10K)
//         for (int i = 1; i <= 25000; i++) {
//             testInvestments.add(createInvestment((long) i, 10L));
//         }

//         when(ipoRepository.findById(1L)).thenReturn(Optional.of(testIPO));
//         when(investmentRepository.findByIpoIdAndStatus(1L, InvestmentStatus.APPROVED))
//             .thenReturn(testInvestments);
//         when(allotmentRepository.save(any(Allotment.class)))
//             .thenAnswer(invocation -> invocation.getArgument(0));
//         when(allotmentLotRepository.saveAll(any()))
//             .thenAnswer(invocation -> invocation.getArgument(0));

//         // Execute
//         AllotmentResult result = allotmentService.performFairLotteryAllotment(1L, 10000);

//         // Verify: All applications processed
//         assertTrue(result.isSuccessful());
//         assertEquals(25000L, result.getTotalApplicationsProcessed());

//         // Total allocated shares: 25000 * 10 / 2.5 = 100000 (but capped at 1000)
//         assertEquals(1000L, result.getTotalSharesAllocated());
//     }

//     @Test
//     @DisplayName("Allocate correct percentage")
//     public void testAllocationPercentage_Correctness() {
//         testInvestments.add(createInvestment(1L, 600L));
//         testInvestments.add(createInvestment(2L, 400L));

//         when(ipoRepository.findById(1L)).thenReturn(Optional.of(testIPO));
//         when(investmentRepository.findByIpoIdAndStatus(1L, InvestmentStatus.APPROVED))
//             .thenReturn(testInvestments);
//         when(allotmentRepository.save(any(Allotment.class)))
//             .thenAnswer(invocation -> invocation.getArgument(0));
//         when(allotmentLotRepository.saveAll(any()))
//             .thenAnswer(invocation -> invocation.getArgument(0));

//         // Execute
//         AllotmentResult result = allotmentService.performFairLotteryAllotment(1L, 10000);

//         // Verify: 1000 shares to 1000 requested = 100%
//         assertEquals(new BigDecimal("100.0000"), result.getOversubscriptionRatio()
//             .multiply(new BigDecimal(100)));
//     }

//     // ==================== HELPER METHODS ====================

//     private Investment createInvestment(Long id, Long sharesRequested) {
//         return Investment.builder()
//             .id(id)
//             .ipo(testIPO)
//             .investor(Investor.builder().id(id).build())
//             .sharesRequested(sharesRequested)
//             .sharesAllotted(0L)
//             .amountInvested(new BigDecimal("10000"))
//             .status(InvestmentStatus.APPROVED)
//             .createdAt(LocalDateTime.now())
//             .updatedAt(LocalDateTime.now())
//             .build();
//     }
// }
