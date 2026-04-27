package com.ipo.service.service;

import com.ipo.entity.model.Investment;
import com.ipo.entity.model.InvestmentStatus;
import com.ipo.entity.model.IPO;
import com.ipo.entity.model.Investor;
import com.ipo.entity.repository.InvestmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ConcurrentOperationServiceTest {

    @Mock
    private InvestmentRepository investmentRepository;

    @InjectMocks
    private ConcurrentOperationService concurrentOperationService;

    private Investment testInvestment;

    @BeforeEach
    public void setUp() {
        IPO ipo = IPO.builder().id(1L).build();
        Investor investor = Investor.builder().id(1L).build();

        testInvestment = Investment.builder()
                .id(1L)
                .ipo(ipo)
                .investor(investor)
                .sharesRequested(1000L)
                .amountInvested(new BigDecimal("50000"))
                .sharesAllotted(0L)
                .status(InvestmentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    public void testProcessInvestmentAllocation() {
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(testInvestment));
        when(investmentRepository.save(any(Investment.class))).thenReturn(testInvestment);

        Investment result = concurrentOperationService.processInvestmentAllocation(1L, 800L);

        assertNotNull(result);
        assertEquals(InvestmentStatus.ALLOTTED, result.getStatus());
        verify(investmentRepository, times(1)).findById(1L);
    }

    @Test
    public void testProcessInvestmentAllocationExceedsRequested() {
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(testInvestment));

        assertThrows(RuntimeException.class, () -> {
            concurrentOperationService.processInvestmentAllocation(1L, 1500L);
        });
    }

    @Test
    public void testReadInvestmentStatus() {
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(testInvestment));

        InvestmentStatus status = concurrentOperationService.readInvestmentStatus(1L);

        assertEquals(InvestmentStatus.PENDING, status);
        verify(investmentRepository, times(1)).findById(1L);
    }

    @Test
    public void testGetActiveLockCount() {
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(testInvestment));
        when(investmentRepository.save(any(Investment.class))).thenReturn(testInvestment);

        concurrentOperationService.processInvestmentAllocation(1L, 500L);

        int lockCount = concurrentOperationService.getActiveLockCount();
        assertTrue(lockCount >= 0);
    }

    @Test
    public void testReleaseLock() {
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(testInvestment));
        when(investmentRepository.save(any(Investment.class))).thenReturn(testInvestment);

        concurrentOperationService.processInvestmentAllocation(1L, 500L);
        concurrentOperationService.releaseLock(1L);

        verify(investmentRepository, times(1)).findById(1L);
    }
}
