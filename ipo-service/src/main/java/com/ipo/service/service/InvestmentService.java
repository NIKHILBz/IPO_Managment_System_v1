package com.ipo.service.service;

import com.ipo.entity.model.Investment;
import com.ipo.entity.model.InvestmentStatus;
import com.ipo.entity.repository.InvestmentRepository;
import com.ipo.service.event.dto.InvestmentEventDto;
import com.ipo.service.kafka.KafkaProducerService;
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
public class InvestmentService {

    @Autowired
    private InvestmentRepository investmentRepository;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    @Cacheable(value = "investments", key = "#id")
    public Optional<Investment> getInvestmentById(Long id) {
        log.debug("Fetching investment with id: {}", id);
        return investmentRepository.findById(id);
    }

    public List<Investment> getInvestmentsByIPOId(Long ipoId) {
        log.debug("Fetching investments for IPO: {}", ipoId);
        return investmentRepository.findByIpoId(ipoId);
    }

    public List<Investment> getInvestmentsByInvestorId(Long investorId) {
        log.debug("Fetching investments for investor: {}", investorId);
        return investmentRepository.findByInvestorId(investorId);
    }

    public List<Investment> getInvestmentsByStatus(InvestmentStatus status) {
        log.debug("Fetching investments with status: {}", status);
        return investmentRepository.findByStatus(status);
    }

    public List<Investment> getAllInvestments() {
        log.debug("Fetching all investments");
        return investmentRepository.findAll();
    }

    @CacheEvict(value = "investments", allEntries = true)
    public Investment createInvestment(Investment investment) {
        log.info("Creating new investment for IPO: {}, Investor: {}",
                investment.getIpo().getId(), investment.getInvestor().getId());
        Investment savedInvestment = investmentRepository.save(investment);

        // Send event
        InvestmentEventDto eventDto = mapToEventDto(savedInvestment);
        kafkaProducerService.sendInvestmentCreatedEvent(eventDto);

        return savedInvestment;
    }

    @CacheEvict(value = "investments", allEntries = true)
    public Investment updateInvestment(Long id, Investment investmentDetails) {
        log.info("Updating investment with id: {}", id);
        Investment investment = investmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Investment not found with id: " + id));

        investment.setSharesRequested(investmentDetails.getSharesRequested());
        investment.setAmountInvested(investmentDetails.getAmountInvested());
        investment.setStatus(investmentDetails.getStatus());

        return investmentRepository.save(investment);
    }

    @CacheEvict(value = "investments", allEntries = true)
    public Investment approveInvestment(Long id) {
        log.info("Approving investment with id: {}", id);
        Investment investment = investmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Investment not found with id: " + id));

        investment.setStatus(InvestmentStatus.APPROVED);
        Investment updatedInvestment = investmentRepository.save(investment);

        InvestmentEventDto eventDto = mapToEventDto(updatedInvestment);
        kafkaProducerService.sendInvestmentProcessedEvent(eventDto);

        return updatedInvestment;
    }

    @CacheEvict(value = "investments", allEntries = true)
    public Investment rejectInvestment(Long id) {
        log.info("Rejecting investment with id: {}", id);
        Investment investment = investmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Investment not found with id: " + id));

        investment.setStatus(InvestmentStatus.REJECTED);
        Investment updatedInvestment = investmentRepository.save(investment);

        InvestmentEventDto eventDto = mapToEventDto(updatedInvestment);
        kafkaProducerService.sendInvestmentProcessedEvent(eventDto);

        return updatedInvestment;
    }

    @CacheEvict(value = "investments", allEntries = true)
    public void deleteInvestment(Long id) {
        log.info("Deleting investment with id: {}", id);
        investmentRepository.deleteById(id);
    }

    private InvestmentEventDto mapToEventDto(Investment investment) {
        return InvestmentEventDto.builder()
                .investmentId(investment.getId())
                .ipoId(investment.getIpo().getId())
                .investorId(investment.getInvestor().getId())
                .investorEmail(investment.getInvestor().getEmail())
                .sharesRequested(investment.getSharesRequested())
                .amountInvested(investment.getAmountInvested())
                .sharesAllotted(investment.getSharesAllotted())
                .status(investment.getStatus().toString())
                .createdAt(investment.getCreatedAt())
                .build();
    }
}
