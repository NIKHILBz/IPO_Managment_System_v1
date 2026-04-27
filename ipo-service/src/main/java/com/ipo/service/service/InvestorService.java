package com.ipo.service.service;

import com.ipo.entity.model.Investor;
import com.ipo.entity.repository.InvestorRepository;
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
public class InvestorService {

    @Autowired
    private InvestorRepository investorRepository;

    @Cacheable(value = "investors", key = "#id")
    public Optional<Investor> getInvestorById(Long id) {
        log.debug("Fetching investor with id: {}", id);
        return investorRepository.findById(id);
    }

    @Cacheable(value = "investors", key = "#email")
    public Optional<Investor> getInvestorByEmail(String email) {
        log.debug("Fetching investor with email: {}", email);
        return investorRepository.findByEmail(email);
    }

    public List<Investor> getAllInvestors() {
        log.debug("Fetching all investors");
        return investorRepository.findAll();
    }

    @CacheEvict(value = "investors", allEntries = true)
    public Investor createInvestor(Investor investor) {
        log.info("Creating new investor: {}", investor.getEmail());
        return investorRepository.save(investor);
    }

    @CacheEvict(value = "investors", allEntries = true)
    public Investor updateInvestor(Long id, Investor investorDetails) {
        log.info("Updating investor with id: {}", id);
        Investor investor = investorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Investor not found with id: " + id));

        investor.setFirstName(investorDetails.getFirstName());
        investor.setLastName(investorDetails.getLastName());
        investor.setPhoneNumber(investorDetails.getPhoneNumber());
        investor.setAddress(investorDetails.getAddress());
        investor.setInvestmentCapacity(investorDetails.getInvestmentCapacity());
        investor.setInvestorType(investorDetails.getInvestorType());

        return investorRepository.save(investor);
    }

    @CacheEvict(value = "investors", allEntries = true)
    public Investor verifyInvestor(Long id) {
        log.info("Verifying investor with id: {}", id);
        Investor investor = investorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Investor not found with id: " + id));

        investor.setIsVerified(true);
        return investorRepository.save(investor);
    }

    @CacheEvict(value = "investors", allEntries = true)
    public void deleteInvestor(Long id) {
        log.info("Deleting investor with id: {}", id);
        investorRepository.deleteById(id);
    }
}
