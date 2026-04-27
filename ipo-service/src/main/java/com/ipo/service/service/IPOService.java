package com.ipo.service.service;

import com.ipo.entity.model.IPO;
import com.ipo.entity.model.IPOStatus;
import com.ipo.entity.repository.IPORepository;
import com.ipo.service.event.dto.IPOEventDto;
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
public class IPOService {

    @Autowired
    private IPORepository ipoRepository;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    @Cacheable(value = "ipos", key = "#id")
    public Optional<IPO> getIPOById(Long id) {
        log.debug("Fetching IPO with id: {}", id);
        return ipoRepository.findById(id);
    }

    public List<IPO> getIPOsByStatus(IPOStatus status) {
        log.debug("Fetching IPOs with status: {}", status);
        return ipoRepository.findByStatus(status);
    }

    public List<IPO> getIPOsByCompanyId(Long companyId) {
        log.debug("Fetching IPOs for company: {}", companyId);
        return ipoRepository.findByCompanyId(companyId);
    }

    public List<IPO> getAllIPOs() {
        log.debug("Fetching all IPOs");
        return ipoRepository.findAll();
    }

    @CacheEvict(value = "ipos", allEntries = true)
    public IPO createIPO(IPO ipo) {
        log.info("Creating new IPO with company id: {}", ipo.getCompany().getId());
        IPO savedIPO = ipoRepository.save(ipo);

        // Send event
        IPOEventDto eventDto = mapToEventDto(savedIPO);
        kafkaProducerService.sendIPOCreatedEvent(eventDto);

        return savedIPO;
    }

    @CacheEvict(value = "ipos", allEntries = true)
    public IPO updateIPO(Long id, IPO ipoDetails) {
        log.info("Updating IPO with id: {}", id);
        IPO ipo = ipoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("IPO not found with id: " + id));

        ipo.setPricePerShare(ipoDetails.getPricePerShare());
        ipo.setTotalSharesOffered(ipoDetails.getTotalSharesOffered());
        ipo.setTotalFundsToRaise(ipoDetails.getTotalFundsToRaise());
        ipo.setStatus(ipoDetails.getStatus());

        IPO updatedIPO = ipoRepository.save(ipo);

        // Send event
        IPOEventDto eventDto = mapToEventDto(updatedIPO);
        kafkaProducerService.sendIPOUpdatedEvent(eventDto);

        return updatedIPO;
    }

    @CacheEvict(value = "ipos", allEntries = true)
    public IPO updateIPOStatus(Long id, IPOStatus status) {
        log.info("Updating IPO status with id: {}, status: {}", id, status);
        IPO ipo = ipoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("IPO not found with id: " + id));

        ipo.setStatus(status);
        IPO updatedIPO = ipoRepository.save(ipo);

        // Send event
        IPOEventDto eventDto = mapToEventDto(updatedIPO);
        kafkaProducerService.sendIPOUpdatedEvent(eventDto);

        return updatedIPO;
    }

    @CacheEvict(value = "ipos", allEntries = true)
    public void deleteIPO(Long id) {
        log.info("Deleting IPO with id: {}", id);
        ipoRepository.deleteById(id);
    }

    private IPOEventDto mapToEventDto(IPO ipo) {
        return IPOEventDto.builder()
                .ipoId(ipo.getId())
                .companyId(ipo.getCompany().getId())
                .companyName(ipo.getCompany().getCompanyName())
                .pricePerShare(ipo.getPricePerShare())
                .totalSharesOffered(ipo.getTotalSharesOffered())
                .totalFundsToRaise(ipo.getTotalFundsToRaise())
                .launchDate(ipo.getLaunchDate())
                .closingDate(ipo.getClosingDate())
                .status(ipo.getStatus().toString())
                .createdAt(ipo.getCreatedAt())
                .build();
    }
}
