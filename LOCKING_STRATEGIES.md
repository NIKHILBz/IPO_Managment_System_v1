# IPO Application Service - Concurrency Control & Locking Strategies

## Overview

The IPO Application Service implements a multi-layered concurrency control approach to handle duplicate applications and concurrent requests safely. This document explains the locking mechanisms employed and their trade-offs.

---

## Problem Statement

**Challenge:** Prevent duplicate IPO applications when multiple concurrent requests arrive for the same user and IPO combination.

**Concurrency Scenario:**
```
Time  User A (Thread 1)          User A (Thread 2)
─────────────────────────────────────────────────
T0    Submit app for IPO-1
T1                               Submit app for IPO-1
T2    Check duplicate ─→ None     │
T3                               Check duplicate ─→ None
T4    Insert ─→ SUCCESS          │
T5                               Insert ─→ SUCCESS (DUPLICATE!)
```

Without proper locking, both threads can pass the duplicate check and insert records, violating the unique constraint.

---

## Solution: Multi-Layer Locking Strategy

### Layer 1: Distributed Locking (Redis)

**What it does:**
- Prevents concurrent execution across multiple application instances
- Uses Redis SET with NX (only if not exists) and EX (expiration)
- Application-level lock with unique token per lock holder

**Implementation:**
```java
String lockToken = distributedLockService.acquireLock("app:ipo:1:investor:100");
// Returns token if acquired, null if already locked
if (lockToken == null) {
    throw new RuntimeException("Submission in progress");
}
try {
    // Protected code
} finally {
    distributedLockService.releaseLock("app:ipo:1:investor:100", lockToken);
}
```

**Use Case:** Multi-instance deployments (e.g., running on Kubernetes with 3+ pod replicas)

**Pros:**
- Works across multiple JVM instances
- Automatic expiration prevents deadlocks (30-second default TTL)
- Prevents concurrent execution entirely

**Cons:**
- Network latency (Redis call)
- Redis dependency
- Not scalable for high-frequency operations (thousands/sec)

**When to Use:**
- Critical sections that must not execute concurrently
- Low-to-medium frequency operations
- When consistency matters more than throughput

---

### Layer 2: Pessimistic Locking (Row-Level)

**What it does:**
- Locks database rows immediately for duration of transaction
- Acquires `FOR UPDATE` lock at database level
- Prevents other transactions from reading/writing locked rows

**Implementation:**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM ApplicationForm a WHERE a.ipo.id = :ipoId AND a.investor.id = :investorId")
Optional<ApplicationForm> findByIpoIdAndInvestorIdWithLock(@Param("ipoId") Long ipoId, @Param("investorId") Long investorId);
```

**Lock Types:**
| Lock Type | Acquired | Blocks | Use Case |
|-----------|----------|--------|----------|
| PESSIMISTIC_READ | `LOCK IN SHARE MODE` | Writes only | Shared read access |
| PESSIMISTIC_WRITE | `FOR UPDATE` | Reads + Writes | Exclusive access |

**Execution Flow:**
```
Transaction 1                   Transaction 2
───────────────────────────────────────────────
BEGIN
SELECT ... FOR UPDATE ─→ Gets lock on row
(Process safely)
                        BEGIN
                        SELECT ... FOR UPDATE ─→ BLOCKED (waiting for lock)
COMMIT ─→ Releases lock
                        ─→ Unblocked, acquires lock
                        SELECT from locked row
                        COMMIT ─→ Releases lock
```

**Pros:**
- Strong consistency guarantee
- No race conditions during transaction
- Works within single database

**Cons:**
- Blocks other transactions (reduced concurrency)
- Higher latency if many writers competing
- Deadlock risk if not careful with lock ordering

**When to Use:**
- High conflict scenarios (many concurrent writes)
- Must prevent intermediate state reads
- Short, write-heavy transactions

---

### Layer 3: Optimistic Locking (Version-Based)

**What it does:**
- Uses `@Version` field in entity (version column in DB)
- No locks held; detects conflicts during UPDATE
- Enables concurrent reads; retries on write conflicts

**Implementation:**
```java
@Entity
public class ApplicationForm {
    @Version
    @Column(nullable = false)
    private Long version;  // Auto-incremented on each update
}
```

**Execution Flow:**
```
Transaction 1                   Transaction 2
───────────────────────────────────────────────
SELECT app, version=1
                        SELECT app, version=1
UPDATE SET status='APPROVED', version=2 WHERE id=1 AND version=1
─→ SUCCESS (rows affected: 1)
                        UPDATE SET status='APPROVED', version=2 WHERE id=1 AND version=1
                        ─→ FAILURE (rows affected: 0)
                        ─→ ObjectOptimisticLockingFailureException
                        [RETRY: SELECT again, get version=2, UPDATE with version=2]
```

**Pros:**
- No database locks; higher concurrency
- Optimized for read-heavy workloads
- Allows true parallel reads

**Cons:**
- Must implement retry logic
- Higher latency if many conflicts
- Application-level retry complexity

**When to Use:**
- Read-heavy workloads (low conflict)
- Long-running transactions
- High concurrency requirements

**Retry Strategy:**
```java
private static final int MAX_RETRY_ATTEMPTS = 3;

public ApplicationForm approveApplication(Long id) {
    int attempts = 0;
    while (attempts < MAX_RETRY_ATTEMPTS) {
        try {
            return approveApplicationWithOptimisticLock(id);
        } catch (ObjectOptimisticLockingFailureException e) {
            attempts++;
            if (attempts >= MAX_RETRY_ATTEMPTS) throw e;
            Thread.sleep(100 * attempts);  // Exponential backoff
        }
    }
}
```

---

### Layer 4: Unique Constraint (Database Enforcement)

**What it does:**
- Final line of defense at database schema level
- Enforces uniqueness of (ipo_id, investor_id) combination
- Raises `DataIntegrityViolationException` on violation

**Implementation:**
```sql
ALTER TABLE application_forms 
ADD CONSTRAINT uk_ipo_investor_application 
UNIQUE(ipo_id, investor_id);
```

**In JPA:**
```java
@Table(
    name = "application_forms",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_ipo_investor_application", columnNames = {"ipo_id", "investor_id"})
    }
)
```

**Pros:**
- Catches bugs in application logic
- Prevents data corruption even if locking fails
- Zero performance overhead

**Cons:**
- Reactive, not preventive (constraint violation already occurred)
- Application must handle `DataIntegrityViolationException`

---

## Locking Comparison Matrix

| Aspect | Distributed Lock | Pessimistic Lock | Optimistic Lock | Unique Constraint |
|--------|-----------------|-----------------|-----------------|-------------------|
| **Scope** | Multiple instances | Single DB | Single DB | Single DB |
| **Consistency** | Strong | Strong | Eventual | Strong |
| **Concurrency** | Low (exclusive) | Medium | High | High |
| **Latency** | Medium (network) | Low | Low | Low |
| **Overhead** | Network call | DB lock | Version check | None |
| **Retry Logic** | Application-managed | Handled by DB | Must implement | Handle exception |
| **Deadlock Risk** | Low (TTL) | High | None | None |
| **Best For** | Critical sections | Write-heavy | Read-heavy | Data integrity |

---

## Application Submission Flow (Multi-Layer Protection)

```
┌─────────────────────────────────────────────────────────────────┐
│ Request: Submit IPO Application (User A, IPO-1)                │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
    ┌─────────────────────────────────────────┐
    │ Layer 1: Acquire Distributed Lock       │
    │ Redis: SET NX "lock:app:ipo:1:user:A"   │
    └─────────────────────────────────────────┘
                      │
        ┌─────────────┴──────────────┐
        │ Lock acquired              │ Lock failed
        ▼                            ▼
    ┌─────────────────────────────┐ Return 409 CONFLICT
    │ Layer 2: Pessimistic Lock   │
    │ SELECT... FOR UPDATE        │
    │ Find existing application   │
    └─────────────────────────────┘
        │
    ┌───┴───────────────────────┐
    │ Not exists                 │ Exists
    ▼                            ▼
┌──────────────────────────┐ Return "You already applied"
│ Layer 3: Insert          │
│ Optimistic Version: 0→1  │
│ Unique Constraint check  │
└──────────────────────────┘
    │
┌───┴────────────────────────────┐
│ Success                         │ Constraint Violation
▼                                 ▼
Release Redis lock          Return "Duplicate (DB enforced)"
Insert Kafka event
Return 201 CREATED
```

---

## Transaction Isolation Levels

The ApplicationService uses specific isolation levels for different operations:

### SERIALIZABLE (For Submission)
```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public ApplicationForm submitApplication(ApplicationForm applicationForm)
```
**Rationale:**
- Strictest level: mutual exclusion on conflicting operations
- Prevents dirty reads, non-repeatable reads, phantom reads
- Matches combined effect of distributed lock + pessimistic lock

### REPEATABLE_READ (For Updates)
```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public ApplicationForm approveApplication(Long id)
```
**Rationale:**
- Prevents dirty and non-repeatable reads
- Allows phantom reads (acceptable for approval workflow)
- Better throughput than SERIALIZABLE

---

## Concurrency Test Scenarios

### Scenario 1: Concurrent Duplicate Submissions
```java
@Test
public void testConcurrentDuplicateSubmissions() throws InterruptedException {
    // 10 threads try to submit same app
    ExecutorService executor = Executors.newFixedThreadPool(10);
    
    for (int i = 0; i < 10; i++) {
        executor.submit(() -> applicationService.submitApplication(app));
    }
    
    // Expected: 1 success, 9 failures (caught by distributed lock + pessimistic lock)
}
```

### Scenario 2: Concurrent Approvals with Optimistic Locking
```java
@Test
public void testConcurrentApprovalRetry() {
    // 2 threads approve same application
    // Thread 1: succeeds first
    // Thread 2: fails with ObjectOptimisticLockingFailureException
    // Thread 2: retries and sees version=1, succeeds
}
```

---

## Production Tuning

### Redis Configuration
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    timeout: 2000ms
    jedis:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
```

### Database Connection Pool
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      max-lifetime: 1800000
```

### MySQL InnoDB Tuning
```sql
-- Optimize lock contention
SET GLOBAL innodb_lock_wait_timeout = 50;  -- Default: 50 seconds

-- For high concurrency
SET GLOBAL innodb_autoinc_lock_mode = 2;  -- Interleaved lock mode
```

---

## Troubleshooting

### Issue: Deadlocks with Pessimistic Locks
**Cause:** Multiple transactions locking rows in different orders
```
Tx1: Lock Row A → Lock Row B
Tx2: Lock Row B → Lock Row A (DEADLOCK!)
```
**Solution:** Always lock rows in same order; use sorted IDs

### Issue: High Optimistic Lock Conflicts
**Cause:** Many concurrent updates to same record
```
Conflict rate = (Write frequency) / (Retry backoff time)
```
**Solution:** 
- Increase retry backoff: `Thread.sleep(100 * attempts)`
- Reduce transaction time
- Distribute writes across multiple records

### Issue: Redis Distributed Lock Timeout
**Cause:** Long operation exceeds lock TTL
```
Operation duration = 40s, Lock TTL = 30s
→ Lock expires while operation running → Race condition
```
**Solution:** 
- Use `acquireLockForLongOperation()` (300s TTL)
- Implement lock renewal/extension

### Issue: Unique Constraint Violation in Production
**Root Cause Diagnosis:**
1. Check if distributed lock failed
2. Check if pessimistic lock was skipped
3. Check if database constraint itself failed

```java
try {
    applicationFormRepository.save(application);
} catch (DataIntegrityViolationException e) {
    log.error("Final line of defense triggered: constraint violation caught", e);
    throw new RuntimeException("Duplicate application detected", e);
}
```

---

## Summary

| Scenario | Recommended | Reason |
|----------|-------------|--------|
| **Single instance, submit app** | Pessimistic + Unique Constraint | Sufficient + simpler |
| **Multi-instance, submit app** | Distributed + Pessimistic + Unique | Maximum safety |
| **Update application status** | Optimistic (retry) | Read-heavy, high concurrency |
| **Approve many apps** | Optimistic with retry | Parallelizable |
| **Report/Analytics query** | No lock needed | Read-only |

---

## References

- [Spring Data JPA Locking](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods/locking.html)
- [MySQL InnoDB Locks](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html)
- [Redis Distributed Locking](https://redis.io/docs/manual/patterns/distributed-locks/)
- [Spring Transactions](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
