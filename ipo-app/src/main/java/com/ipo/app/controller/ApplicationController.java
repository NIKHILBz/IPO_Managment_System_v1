package com.ipo.app.controller;

import com.ipo.entity.model.ApplicationForm;
import com.ipo.entity.model.ApplicationStatus;
import com.ipo.service.service.ApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.*;
import java.util.List;


@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private static final Logger log = LoggerFactory.getLogger(ApplicationController.class);

    @Autowired
    private ApplicationService applicationService;

    @GetMapping("/{id}")
    public ResponseEntity<?> getApplicationById(@PathVariable Long id) {
        try {
            return applicationService.getApplicationById(id)
                    .map(application -> ResponseEntity.ok(application))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error fetching application", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching application");
        }
    }

    @GetMapping("/number/{applicationNumber}")
    public ResponseEntity<?> getApplicationByNumber(@PathVariable String applicationNumber) {
        try {
            return applicationService.getApplicationByNumber(applicationNumber)
                    .map(application -> ResponseEntity.ok(application))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error fetching application", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching application");
        }
    }

    @GetMapping("/ipo/{ipoId}")
    public ResponseEntity<?> getApplicationsByIPO(@PathVariable Long ipoId) {
        try {
            List<ApplicationForm> applications = applicationService.getApplicationsByIPOId(ipoId);
            return ResponseEntity.ok(applications);
        } catch (Exception e) {
            log.error("Error fetching applications", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching applications");
        }
    }

    @GetMapping("/investor/{investorId}")
    public ResponseEntity<?> getApplicationsByInvestor(@PathVariable Long investorId) {
        try {
            List<ApplicationForm> applications = applicationService.getApplicationsByInvestorId(investorId);
            return ResponseEntity.ok(applications);
        } catch (Exception e) {
            log.error("Error fetching applications", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching applications");
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllApplications() {
        try {
            List<ApplicationForm> applications = applicationService.getAllApplications();
            return ResponseEntity.ok(applications);
        } catch (Exception e) {
            log.error("Error fetching applications", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching applications");
        }
    }

    @PostMapping
    public ResponseEntity<?> submitApplication(@RequestBody ApplicationForm applicationForm) {
        try {
            ApplicationForm submittedApplication = applicationService.submitApplication(applicationForm);
            return ResponseEntity.status(HttpStatus.CREATED).body(submittedApplication);
        } catch (Exception e) {
            log.error("Error submitting application", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error submitting application");
        }
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<?> approveApplication(@PathVariable Long id) {
        try {
            ApplicationForm approvedApplication = applicationService.approveApplication(id);
            return ResponseEntity.ok(approvedApplication);
        } catch (Exception e) {
            log.error("Error approving application", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error approving application");
        }
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<?> rejectApplication(@PathVariable Long id, @RequestParam String reason) {
        try {
            ApplicationForm rejectedApplication = applicationService.rejectApplication(id, reason);
            return ResponseEntity.ok(rejectedApplication);
        } catch (Exception e) {
            log.error("Error rejecting application", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error rejecting application");
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateApplicationStatus(@PathVariable Long id, @RequestParam ApplicationStatus status) {
        try {
            ApplicationForm updatedApplication = applicationService.updateApplicationStatus(id, status);
            return ResponseEntity.ok(updatedApplication);
        } catch (Exception e) {
            log.error("Error updating application status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating application status");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteApplication(@PathVariable Long id) {
        try {
            applicationService.deleteApplication(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting application", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting application");
        }
    }
}
