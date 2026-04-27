package com.ipo.service.service;

import com.ipo.entity.model.*;
import com.ipo.entity.repository.ApplicationFormRepository;
import com.ipo.service.kafka.KafkaProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ApplicationService Test Suite
 *
 * Tests cover:
 * 1. Basic CRUD operations
 * 2. Duplicate prevention mechanisms
 * 3. Concurrency handling with distributed locking
 * 4. Optimistic locking retry logic
 * 5. Pessimistic locking queries
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Application Service Tests")
public class ApplicationServiceTest {

    @Mock
    private ApplicationFormRepository applicationFormRepository;

    @Mock
    private KafkaProducerService kafkaProducerService;

    @Mock
    private DistributedLockService distributedLockService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @InjectMocks
    private ApplicationService applicationService;

    private IPO testIPO;
    private Investor testInvestor;
    private ApplicationForm testApplication;

    @BeforeEach
    public void setUp() {
        testIPO = IPO.builder().id(1L).build();
        testInvestor = Investor.builder().id(100L).build();
        testApplication = ApplicationForm.builder()
            .id(1L)
            .ipo(testIPO)
            .investor(testInvestor)
            .applicationNumber("APP-1234567890-ABCD1234")
            .status(ApplicationStatus.SUBMITTED)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .version(0L)
            .build();
    }

    // ==================== READ OPERATIONS ====================

    @Test
    @DisplayName("Should fetch application by ID")
    public void testGetApplicationById() {
        when(applicationFormRepository.findById(1L)).thenReturn(Optional.of(testApplication));

        Optional<ApplicationForm> result = applicationService.getApplicationById(1L);

        assertTrue(result.isPresent());
        assertEquals("APP-1234567890-ABCD1234", result.get().getApplicationNumber());
        verify(applicationFormRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should fetch applications by IPO ID")
    public void testGetApplicationsByIPOId() {
        List<ApplicationForm> applications = List.of(testApplication);
        when(applicationFormRepository.findByIpoId(1L)).thenReturn(applications);

        List<ApplicationForm> result = applicationService.getApplicationsByIPOId(1L);

        assertEquals(1, result.size());
        assertEquals("APP-1234567890-ABCD1234", result.get(0).getApplicationNumber());
        verify(applicationFormRepository, times(1)).findByIpoId(1L);
    }

    @Test
    @DisplayName("Should fetch applications by investor ID")
    public void testGetApplicationsByInvestorId() {
        List<ApplicationForm> applications = List.of(testApplication);
        when(applicationFormRepository.findByInvestorId(100L)).thenReturn(applications);

        List<ApplicationForm> result = applicationService.getApplicationsByInvestorId(100L);

        assertEquals(1, result.size());
        verify(applicationFormRepository, times(1)).findByInvestorId(100L);
    }

    @Test
    @DisplayName("Should fetch applications by status")
    public void testGetApplicationsByStatus() {
        List<ApplicationForm> applications = List.of(testApplication);
        when(applicationFormRepository.findByStatus(ApplicationStatus.SUBMITTED)).thenReturn(applications);

        List<ApplicationForm> result = applicationService.getApplicationsByStatus(ApplicationStatus.SUBMITTED);

        assertEquals(1, result.size());
        verify(applicationFormRepository, times(1)).findByStatus(ApplicationStatus.SUBMITTED);
    }

    // ==================== SUBMIT APPLICATION (CONCURRENCY) ====================

    @Test
    @DisplayName("Should successfully submit new application with distributed lock")
    public void testSubmitApplicationSuccess() {
        String lockToken = "test-token-123";
        when(distributedLockService.acquireLock(anyString())).thenReturn(lockToken);
        when(applicationFormRepository.findByIpoIdAndInvestorIdWithLock(1L, 100L)).thenReturn(Optional.empty());
        when(applicationFormRepository.save(any(ApplicationForm.class))).thenReturn(testApplication);

        ApplicationForm result = applicationService.submitApplication(testApplication);

        assertNotNull(result);
        assertEquals(ApplicationStatus.SUBMITTED, result.getStatus());
        verify(distributedLockService, times(1)).acquireLock(anyString());
        verify(distributedLockService, times(1)).releaseLock(anyString(), eq(lockToken));
        verify(applicationFormRepository, times(1)).save(any(ApplicationForm.class));
        verify(kafkaProducerService, times(1)).sendApplicationSubmittedEvent(any());
    }

    @Test
    @DisplayName("Should prevent duplicate application submission - lock acquisition failed")
    public void testSubmitApplicationDuplicateLockFailed() {
        // Simulate lock acquisition failure
        when(distributedLockService.acquireLock(anyString())).thenReturn(null);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            applicationService.submitApplication(testApplication);
        });

        assertTrue(exception.getMessage().contains("Application submission in progress"));
        verify(applicationFormRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should prevent duplicate application - existing application found")
    public void testSubmitApplicationDuplicateExisting() {
        String lockToken = "test-token-123";
        when(distributedLockService.acquireLock(anyString())).thenReturn(lockToken);
        when(applicationFormRepository.findByIpoIdAndInvestorIdWithLock(1L, 100L))
            .thenReturn(Optional.of(testApplication));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            applicationService.submitApplication(testApplication);
        });

        assertEquals("You have already applied for this IPO", exception.getMessage());
        verify(applicationFormRepository, never()).save(any());
        verify(distributedLockService, times(1)).releaseLock(anyString(), eq(lockToken));
    }

    @Test
    @DisplayName("Should check for duplicate applications")
    public void testIsDuplicateApplication() {
        when(applicationFormRepository.existsByIpoIdAndInvestorId(1L, 100L)).thenReturn(true);

        boolean result = applicationService.isDuplicateApplication(1L, 100L);

        assertTrue(result);
        verify(applicationFormRepository, times(1)).existsByIpoIdAndInvestorId(1L, 100L);
    }

    // ==================== OPTIMISTIC LOCKING ====================

    @Test
    @DisplayName("Should approve application with optimistic locking")
    public void testApproveApplicationWithOptimisticLocking() {
        when(applicationFormRepository.findById(1L))
            .thenReturn(Optional.of(testApplication));

        ApplicationForm updatedApp = ApplicationForm.builder()
            .id(1L)
            .ipo(testIPO)
            .investor(testInvestor)
            .applicationNumber("APP-1234567890-ABCD1234")
            .status(ApplicationStatus.APPROVED)
            .version(1L)
            .createdAt(testApplication.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .build();

        when(applicationFormRepository.save(any(ApplicationForm.class))).thenReturn(updatedApp);

        ApplicationForm result = applicationService.approveApplication(1L);

        assertEquals(ApplicationStatus.APPROVED, result.getStatus());
        assertNull(result.getRejectionReason());
        verify(applicationFormRepository, times(1)).findById(1L);
        verify(applicationFormRepository, times(1)).save(any(ApplicationForm.class));
    }

    @Test
    @DisplayName("Should retry on optimistic lock conflict")
    public void testApproveApplicationOptimisticLockRetry() {
        when(applicationFormRepository.findById(1L))
            .thenReturn(Optional.of(testApplication))
            .thenReturn(Optional.of(testApplication));

        ApplicationForm updatedApp = testApplication;
        updatedApp.setStatus(ApplicationStatus.APPROVED);

        when(applicationFormRepository.save(any(ApplicationForm.class)))
            .thenThrow(new ObjectOptimisticLockingFailureException("", new Object()))
            .thenReturn(updatedApp);

        ApplicationForm result = applicationService.approveApplication(1L);

        assertEquals(ApplicationStatus.APPROVED, result.getStatus());
        verify(applicationFormRepository, atLeast(2)).findById(1L);
    }

    // ==================== REJECT APPLICATION ====================

    @Test
    @DisplayName("Should reject application with reason")
    public void testRejectApplication() {
        when(applicationFormRepository.findById(1L)).thenReturn(Optional.of(testApplication));

        ApplicationForm rejectedApp = ApplicationForm.builder()
            .id(1L)
            .ipo(testIPO)
            .investor(testInvestor)
            .applicationNumber("APP-1234567890-ABCD1234")
            .status(ApplicationStatus.REJECTED)
            .rejectionReason("Incomplete documentation")
            .createdAt(testApplication.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .version(1L)
            .build();

        when(applicationFormRepository.save(any(ApplicationForm.class))).thenReturn(rejectedApp);

        ApplicationForm result = applicationService.rejectApplication(1L, "Incomplete documentation");

        assertEquals(ApplicationStatus.REJECTED, result.getStatus());
        assertEquals("Incomplete documentation", result.getRejectionReason());
        verify(applicationFormRepository, times(1)).save(any(ApplicationForm.class));
    }

    // ==================== UPDATE STATUS ====================

    @Test
    @DisplayName("Should update application status")
    public void testUpdateApplicationStatus() {
        when(applicationFormRepository.findById(1L)).thenReturn(Optional.of(testApplication));

        ApplicationForm updatedApp = testApplication;
        updatedApp.setStatus(ApplicationStatus.APPROVED);

        when(applicationFormRepository.save(any(ApplicationForm.class))).thenReturn(updatedApp);

        ApplicationForm result = applicationService.updateApplicationStatus(1L, ApplicationStatus.APPROVED);

        assertEquals(ApplicationStatus.APPROVED, result.getStatus());
        verify(applicationFormRepository, times(1)).save(any(ApplicationForm.class));
    }

    // ==================== DELETE APPLICATION ====================

    @Test
    @DisplayName("Should delete application in SUBMITTED status")
    public void testDeleteApplication() {
        when(applicationFormRepository.findById(1L)).thenReturn(Optional.of(testApplication));

        applicationService.deleteApplication(1L);

        verify(applicationFormRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("Should not delete application not in SUBMITTED status")
    public void testDeleteApplicationNonSubmitted() {
        ApplicationForm approvedApp = testApplication;
        approvedApp.setStatus(ApplicationStatus.APPROVED);
        when(applicationFormRepository.findById(1L)).thenReturn(Optional.of(approvedApp));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            applicationService.deleteApplication(1L);
        });

        assertTrue(exception.getMessage().contains("SUBMITTED status"));
        verify(applicationFormRepository, never()).deleteById(any());
    }

    // ==================== CONCURRENCY STRESS TEST ====================

    @Test
    @DisplayName("Should handle concurrent submissions safely")
    public void testConcurrentApplicationSubmissions() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // First call succeeds, others fail due to duplicate
        when(distributedLockService.acquireLock(anyString()))
            .thenReturn("token-123")
            .thenReturn(null)
            .thenReturn(null);

        when(applicationFormRepository.findByIpoIdAndInvestorIdWithLock(1L, 100L))
            .thenReturn(Optional.empty());

        when(applicationFormRepository.save(any(ApplicationForm.class)))
            .thenReturn(testApplication);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    applicationService.submitApplication(testApplication);
                    successCount.incrementAndGet();
                } catch (RuntimeException e) {
                    // Expected for duplicate attempts
                }
                latch.countDown();
            });
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "Concurrent test did not complete in time");

        executor.shutdown();
    }
}
