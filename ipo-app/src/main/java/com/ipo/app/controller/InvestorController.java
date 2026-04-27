package com.ipo.app.controller;

import com.ipo.entity.model.Investor;
import com.ipo.service.service.InvestorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.*;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/investors")
public class InvestorController {

    private static final Logger log = LoggerFactory.getLogger(InvestorController.class);
    
    @Autowired
    private InvestorService investorService;

    @GetMapping("/{id}")
    public ResponseEntity<?> getInvestorById(@PathVariable Long id) {
        try {
            return investorService.getInvestorById(id)
                    .map(investor -> ResponseEntity.ok(investor))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error fetching investor", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching investor");
        }
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<?> getInvestorByEmail(@PathVariable String email) {
        try {
            return investorService.getInvestorByEmail(email)
                    .map(investor -> ResponseEntity.ok(investor))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error fetching investor by email", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching investor");
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllInvestors() {
        try {
            List<Investor> investors = investorService.getAllInvestors();
            return ResponseEntity.ok(investors);
        } catch (Exception e) {
            log.error("Error fetching investors", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching investors");
        }
    }

    @PostMapping
    public ResponseEntity<?> createInvestor(@RequestBody Investor investor) {
        try {
            Investor createdInvestor = investorService.createInvestor(investor);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdInvestor);
        } catch (Exception e) {
            log.error("Error creating investor", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating investor");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateInvestor(@PathVariable Long id, @RequestBody Investor investor) {
        try {
            Investor updatedInvestor = investorService.updateInvestor(id, investor);
            return ResponseEntity.ok(updatedInvestor);
        } catch (Exception e) {
            log.error("Error updating investor", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating investor");
        }
    }

    @PatchMapping("/{id}/verify")
    public ResponseEntity<?> verifyInvestor(@PathVariable Long id) {
        try {
            Investor verifiedInvestor = investorService.verifyInvestor(id);
            return ResponseEntity.ok(verifiedInvestor);
        } catch (Exception e) {
            log.error("Error verifying investor", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error verifying investor");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteInvestor(@PathVariable Long id) {
        try {
            investorService.deleteInvestor(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting investor", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting investor");
        }
    }
}
