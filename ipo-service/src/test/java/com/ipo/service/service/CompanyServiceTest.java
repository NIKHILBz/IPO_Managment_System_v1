package com.ipo.service.service;

import com.ipo.entity.model.Company;
import com.ipo.entity.repository.CompanyRepository;
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
public class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    private CompanyService companyService;

    private Company testCompany;

    @BeforeEach
    public void setUp() {
        testCompany = Company.builder()
                .id(1L)
                .companyName("Test Company")
                .industry("Technology")
                .description("A test company")
                .foundedYear(new BigDecimal("2020"))
                .ceoName("John Doe")
                .headquarters("New York")
                .currentValuation(new BigDecimal("1000000000"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    public void testGetCompanyById() {
        when(companyRepository.findById(1L)).thenReturn(Optional.of(testCompany));

        Optional<Company> result = companyService.getCompanyById(1L);

        assertTrue(result.isPresent());
        assertEquals(testCompany.getCompanyName(), result.get().getCompanyName());
        verify(companyRepository, times(1)).findById(1L);
    }

    @Test
    public void testGetAllCompanies() {
        List<Company> companies = Arrays.asList(testCompany);
        when(companyRepository.findAll()).thenReturn(companies);

        List<Company> result = companyService.getAllCompanies();

        assertEquals(1, result.size());
        assertEquals(testCompany.getCompanyName(), result.get(0).getCompanyName());
        verify(companyRepository, times(1)).findAll();
    }

    @Test
    public void testCreateCompany() {
        when(companyRepository.save(any(Company.class))).thenReturn(testCompany);

        Company result = companyService.createCompany(testCompany);

        assertNotNull(result);
        assertEquals(testCompany.getCompanyName(), result.getCompanyName());
        verify(companyRepository, times(1)).save(any(Company.class));
    }

    @Test
    public void testUpdateCompany() {
        Company updatedCompany = testCompany;
        updatedCompany.setCompanyName("Updated Company");

        when(companyRepository.findById(1L)).thenReturn(Optional.of(testCompany));
        when(companyRepository.save(any(Company.class))).thenReturn(updatedCompany);

        Company result = companyService.updateCompany(1L, updatedCompany);

        assertNotNull(result);
        assertEquals("Updated Company", result.getCompanyName());
        verify(companyRepository, times(1)).findById(1L);
        verify(companyRepository, times(1)).save(any(Company.class));
    }

    @Test
    public void testDeleteCompany() {
        companyService.deleteCompany(1L);
        verify(companyRepository, times(1)).deleteById(1L);
    }
}
