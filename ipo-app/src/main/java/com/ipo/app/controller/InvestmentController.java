package com.ipo.app.controller;

import com.ipo.entity.model.Investment;
import com.ipo.entity.model.InvestmentStatus;
import com.ipo.service.service.InvestmentService;
import com.ipo.service.service.ConcurrentOperationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.*;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/investments")
public class InvestmentController {

    private static final Logger log = LoggerFactory.getLogger(InvestmentController.class);
   
    @Autowired
    private InvestmentService investmentService;

    @Autowired
    private ConcurrentOperationService concurrentOperationService;

    @GetMapping("/{id}")
    public ResponseEntity<?> getInvestmentById(@PathVariable Long id) {
        try {
            return investmentService.getInvestmentById(id)
                    .map(investment -> ResponseEntity.ok(investment))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error fetching investment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching investment");
        }
    }

    @GetMapping("/ipo/{ipoId}")
    public ResponseEntity<?> getInvestmentsByIPO(@PathVariable Long ipoId) {
        try {
            List<Investment> investments = investmentService.getInvestmentsByIPOId(ipoId);
            return ResponseEntity.ok(investments);
        } catch (Exception e) {
            log.error("Error fetching investments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching investments");
        }
    }

    @GetMapping("/investor/{investorId}")
    public ResponseEntity<?> getInvestmentsByInvestor(@PathVariable Long investorId) {
        try {
            List<Investment> investments = investmentService.getInvestmentsByInvestorId(investorId);
            return ResponseEntity.ok(investments);
        } catch (Exception e) {
            log.error("Error fetching investments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching investments");
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllInvestments() {
        try {
            List<Investment> investments = investmentService.getAllInvestments();
            return ResponseEntity.ok(investments);
        } catch (Exception e) {
            log.error("Error fetching investments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching investments");
        }
    }

    @PostMapping
    public ResponseEntity<?> createInvestment(@RequestBody Investment investment) {
        try {
            Investment createdInvestment = investmentService.createInvestment(investment);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdInvestment);
        } catch (Exception e) {
            log.error("Error creating investment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating investment");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateInvestment(@PathVariable Long id, @RequestBody Investment investment) {
        try {
            Investment updatedInvestment = investmentService.updateInvestment(id, investment);
            return ResponseEntity.ok(updatedInvestment);
        } catch (Exception e) {
            log.error("Error updating investment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating investment");
        }
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<?> approveInvestment(@PathVariable Long id) {
        try {
            Investment approvedInvestment = investmentService.approveInvestment(id);
            return ResponseEntity.ok(approvedInvestment);
        } catch (Exception e) {
            log.error("Error approving investment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error approving investment");
        }
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<?> rejectInvestment(@PathVariable Long id) {
        try {
            Investment rejectedInvestment = investmentService.rejectInvestment(id);
            return ResponseEntity.ok(rejectedInvestment);
        } catch (Exception e) {
            log.error("Error rejecting investment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error rejecting investment");
        }
    }

    @PatchMapping("/{id}/allocate")
    public ResponseEntity<?> allocateShares(@PathVariable Long id, @RequestParam Long shares) {
        try {
            Investment investment = concurrentOperationService.processInvestmentAllocation(id, shares);
            return ResponseEntity.ok(investment);
        } catch (Exception e) {
            log.error("Error allocating shares", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error allocating shares");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteInvestment(@PathVariable Long id) {
        try {
            investmentService.deleteInvestment(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting investment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting investment");
        }
    }
}
