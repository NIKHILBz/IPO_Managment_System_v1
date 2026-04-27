package com.ipo.service.service;

import com.ipo.entity.model.Investor;
import com.ipo.entity.repository.InvestorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InvestorServiceTest {

    @Mock
    private InvestorRepository investorRepository;

    @InjectMocks
    private InvestorService investorService;

    private Investor testInvestor;

    @BeforeEach
    public void setUp() {
        testInvestor = Investor.builder()
                .id(1L)
                .email("test@example.com")
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("1234567890")
                .address("123 Main St")
                .investmentCapacity(new BigDecimal("1000000"))
                .investorType("Individual")
                .isVerified(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    public void testGetInvestorById() {
        when(investorRepository.findById(1L)).thenReturn(Optional.of(testInvestor));

        Optional<Investor> result = investorService.getInvestorById(1L);

        assertTrue(result.isPresent());
        assertEquals(testInvestor.getEmail(), result.get().getEmail());
        verify(investorRepository, times(1)).findById(1L);
    }

    @Test
    public void testGetInvestorByEmail() {
        when(investorRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testInvestor));

        Optional<Investor> result = investorService.getInvestorByEmail("test@example.com");

        assertTrue(result.isPresent());
        assertEquals(testInvestor.getEmail(), result.get().getEmail());
        verify(investorRepository, times(1)).findByEmail("test@example.com");
    }

    @Test
    public void testCreateInvestor() {
        when(investorRepository.save(any(Investor.class))).thenReturn(testInvestor);

        Investor result = investorService.createInvestor(testInvestor);

        assertNotNull(result);
        assertEquals(testInvestor.getEmail(), result.getEmail());
        verify(investorRepository, times(1)).save(any(Investor.class));
    }

    @Test
    public void testVerifyInvestor() {
        when(investorRepository.findById(1L)).thenReturn(Optional.of(testInvestor));
        testInvestor.setIsVerified(true);
        when(investorRepository.save(any(Investor.class))).thenReturn(testInvestor);

        Investor result = investorService.verifyInvestor(1L);

        assertTrue(result.getIsVerified());
        verify(investorRepository, times(1)).findById(1L);
        verify(investorRepository, times(1)).save(any(Investor.class));
    }

    @Test
    public void testDeleteInvestor() {
        investorService.deleteInvestor(1L);
        verify(investorRepository, times(1)).deleteById(1L);
    }
}
