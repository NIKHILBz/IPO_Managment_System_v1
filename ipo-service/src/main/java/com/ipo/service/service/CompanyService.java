package com.ipo.service.service;

import com.ipo.entity.model.Company;
import com.ipo.entity.repository.CompanyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
public class CompanyService {

    @Autowired
    private CompanyRepository companyRepository;

    @Cacheable(value = "companies", key = "#id")
    public Optional<Company> getCompanyById(Long id) {
        log.debug("Fetching company with id: {}", id);
        return companyRepository.findById(id);
    }

    @Cacheable(value = "companies", key = "#name")
    public Optional<Company> getCompanyByName(String name) {
        log.debug("Fetching company with name: {}", name);
        return companyRepository.findByCompanyName(name);
    }

    public List<Company> getAllCompanies() {
        log.debug("Fetching all companies");
        return companyRepository.findAll();
    }

    @CacheEvict(value = "companies", allEntries = true)
    public Company createCompany(Company company) {
        log.info("Creating new company: {}", company.getCompanyName());
        return companyRepository.save(company);
    }

    @CacheEvict(value = "companies", allEntries = true)
    public Company updateCompany(Long id, Company companyDetails) {
        log.info("Updating company with id: {}", id);
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found with id: " + id));

        company.setCompanyName(companyDetails.getCompanyName());
        company.setIndustry(companyDetails.getIndustry());
        company.setDescription(companyDetails.getDescription());
        company.setCeoName(companyDetails.getCeoName());
        company.setHeadquarters(companyDetails.getHeadquarters());
        company.setCurrentValuation(companyDetails.getCurrentValuation());

        return companyRepository.save(company);
    }

    @CacheEvict(value = "companies", allEntries = true)
    public void deleteCompany(Long id) {
        log.info("Deleting company with id: {}", id);
        companyRepository.deleteById(id);
    }
}
