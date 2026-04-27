package com.ipo.app.controller;

import com.ipo.entity.model.IPO;
import com.ipo.entity.model.IPOStatus;
import com.ipo.service.service.IPOService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.*;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/ipos")
public class IPOController {

    private static final Logger log = LoggerFactory.getLogger(IPOController.class);
   
    @Autowired
    private IPOService ipoService;

    @GetMapping("/{id}")
    public ResponseEntity<?> getIPOById(@PathVariable Long id) {
        try {
            return ipoService.getIPOById(id)
                    .map(ipo -> ResponseEntity.ok(ipo))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error fetching IPO", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching IPO");
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllIPOs() {
        try {
            List<IPO> ipos = ipoService.getAllIPOs();
            return ResponseEntity.ok(ipos);
        } catch (Exception e) {
            log.error("Error fetching IPOs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching IPOs");
        }
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<?> getIPOsByStatus(@PathVariable IPOStatus status) {
        try {
            List<IPO> ipos = ipoService.getIPOsByStatus(status);
            return ResponseEntity.ok(ipos);
        } catch (Exception e) {
            log.error("Error fetching IPOs by status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching IPOs");
        }
    }

    @PostMapping
    public ResponseEntity<?> createIPO(@RequestBody IPO ipo) {
        try {
            IPO createdIPO = ipoService.createIPO(ipo);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdIPO);
        } catch (Exception e) {
            log.error("Error creating IPO", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating IPO");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateIPO(@PathVariable Long id, @RequestBody IPO ipo) {
        try {
            IPO updatedIPO = ipoService.updateIPO(id, ipo);
            return ResponseEntity.ok(updatedIPO);
        } catch (Exception e) {
            log.error("Error updating IPO", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating IPO");
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateIPOStatus(@PathVariable Long id, @RequestParam IPOStatus status) {
        try {
            IPO updatedIPO = ipoService.updateIPOStatus(id, status);
            return ResponseEntity.ok(updatedIPO);
        } catch (Exception e) {
            log.error("Error updating IPO status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating IPO status");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteIPO(@PathVariable Long id) {
        try {
            ipoService.deleteIPO(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting IPO", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting IPO");
        }
    }
}
