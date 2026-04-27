# IPO Allotment Batching Strategy & Optimization

## Overview

This document describes the batching strategy for processing IPO allotments at scale. Addresses how to handle 1M+ applications efficiently while maintaining consistency and memory efficiency.

---

## Problem: Processing at Scale

### Challenge
```
Scenario: IPO receives 5 million applications
           Total available shares: 100,000
           Oversubscription ratio: 50x

Question: How to fairly allocate while processing efficiently?
```

### Constraints
- **Memory:** Limited heap (typically 1-4 GB for JVM)
- **Database:** Connection pool limited (20-50 connections)
- **Latency:** Admin wants results in < 30 minutes
- **Consistency:** Cannot have partial allocations or duplicates

### Solution
**Pagination-based batch processing with transactional boundaries**

---

## Batching Strategy

### Architecture

```
┌─────────────────────────────────────────┐
│   AdminController.startAllotment()      │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│   AllotmentService.performFairLottery() │
└─────────────────┬───────────────────────┘
                  │
          ┌───────┴────────┐
          │                │
    ┌─────▼──────┐    ┌────▼─────┐
    │ Step 1: Load│    │ Step 2:  │
    │ calculate   │    │ Seed & Ratio
    └─────┬──────┘    └────┬─────┘
          │                │
          └────────┬───────┘
                   │
    ┌──────────────▼─────────────────┐
    │ Step 3: Batch Processing Loop  │
    │                                │
    │  For each page (1-5000):       │
    │   ├─ Query investments (page)  │
    │   ├─ Generate lot numbers      │
    │   ├─ Calculate allocations     │
    │   ├─ Create AllotmentLot batch │
    │   ├─ Update Investment status  │
    │   ├─ Save to DB (transaction)  │
    │   └─ Log progress              │
    └──────────────┬─────────────────┘
                   │
          ┌────────▼────────┐
          │  Step 4: Finalize
          │  Update Allotment
          │  status=COMPLETED
          └─────────────────┘
```

### Batch Processing Loop

```java
// Pseudocode
for (int page = 0; page * pageSize < totalApplications; page++) {
    // 1. Load page of applications
    List<Investment> batch = queryPage(ipoId, page, pageSize);
    
    if (batch.isEmpty()) break;
    
    // 2. Process batch within transaction
    AllotmentBatchResult result = processBatch(
        allotment, 
        batch, 
        ratio, 
        randomSeed);
    
    totalProcessed += result.processedCount;
    totalAllocated += result.totalAllocated;
    
    // 3. Log progress
    log.info("Batch {}: {}/{} processed", 
        page, 
        totalProcessed, 
        totalApplications);
    
    // 4. Optional: backpressure
    Thread.sleep(100); // Let DB catch up
}
```

---

## Configuration Tuning

### Parameter: Batch Size (Page Size)

```yaml
allotment:
  batch-size: 10000  # Applications per batch
  page-sleep-ms: 100 # Backpressure between pages
```

#### Impact Analysis

| Batch Size | DB Queries | Time | Memory | Transaction Size |
|------------|-----------|------|--------|-----------------|
| 1,000     | 5,000     | 22m  | Low    | Small           |
| **10,000** | **500**   | **2.2m** | **Medium** | **Medium**  |
| 50,000    | 100       | 26s  | High   | Large           |
| 100,000   | 50        | 13s  | Very High | Very Large  |

**Recommendation:** 10,000 for optimal balance

### Calculation: Optimal Batch Size

```
Dataset: 5 million applications
Available memory: 2 GB heap
Target latency: < 30 minutes

Memory per application (estimate):
  - Entity object: ~500 bytes
  - AllotmentLot object: ~400 bytes
  - Lot assignment object: ~300 bytes
  - Overhead: ~200 bytes
  ─────────────────────────────
  Total per app: ~1.4 KB

Batch size: Available memory / Per-app memory
         = 1.5 GB / 1.4 KB
         = ~1 million applications

But we need safety margin (50%):
  Practical batch size = 1,000,000 * 0.5 = 500,000

But DB may choke on 500K in single transaction:
  Empirical sweet spot = 10,000 - 50,000

Chosen: 10,000 (conservative, proven)
```

---

## Performance Optimization

### 1. Batch Insert Optimization

```java
// BEFORE: Individual saves (slow)
for (AllotmentLot lot : allocationLots) {
    allotmentLotRepository.save(lot);  // 1 INSERT per lot
}
// Total: N SQL INSERTs

// AFTER: Batch save (fast)
allotmentLotRepository.saveAll(allocationLots);  // Bulk INSERT
// Total: 1 SQL INSERT (may be split into chunks by DB driver)
```

**Performance Impact:**
```
N = 10,000 AllotmentLot records

Individual saves:  10,000 network round-trips → ~5 seconds
Batch save:       1-2 network round-trips → ~50 milliseconds
───────────────────────────────────────────────────────────
Speedup: 100x faster
```

### 2. Batch Update Optimization

```sql
-- BEFORE: Individual updates (slow)
UPDATE investments SET status = 'ALLOTTED', shares_allotted = 462 WHERE id = 1;
UPDATE investments SET status = 'ALLOTTED', shares_allotted = 308 WHERE id = 2;
...
(10,000 UPDATE statements)

-- AFTER: Batch update (fast)
UPDATE investments 
SET status = 'ALLOTTED'
WHERE id IN (1, 2, 3, ..., 10000);
```

**Performance Impact:**
```
Individual updates: 10,000 statements → ~8 seconds
Batch update:      1 statement → ~100 milliseconds
────────────────────────────────────────────────────
Speedup: 80x faster
```

### 3. Connection Pooling

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50        # Connections for parallel queries
      minimum-idle: 10             # Keep warm
      connection-timeout: 30000    # 30s timeout
      idle-timeout: 600000         # Reclaim after 10 min
      max-lifetime: 1800000        # 30 min max connection age
```

**Impact:**
- Without pooling: New connection per query → TCP handshake overhead
- With pooling: Reuse connections → Sub-millisecond access

### 4. Index Utilization

```sql
-- Index on (ipo_id, status) speeds up filtering
CREATE INDEX idx_investment_ipo_status 
ON investments(ipo_id, status);

-- Index on (allotment_id) speeds up allotment_lots queries
CREATE INDEX idx_allot_lot_allotment_id 
ON allotment_lots(allotment_id);

-- Index on (lot_number) for result retrieval
CREATE INDEX idx_allot_lot_lot_number 
ON allotment_lots(lot_number);
```

**Query Execution:**
```
Without indexes:  Full table scan → ~5s per batch
With indexes:     Index seek → ~50ms per batch
──────────────────────────────────────────────────
Speedup: 100x faster
```

### 5. Pagination Efficiency

```java
// CORRECT: Paginated query (efficient)
for (int page = 0; page < totalPages; page++) {
    Page<Investment> batch = investmentRepository.findByIpoIdAndStatus(
        ipoId, 
        APPROVED,
        PageRequest.of(page, 10000));
    
    // Process batch
}
// Total queries: 500 (for 5M records with 10K page size)

// INCORRECT: Load all into memory (crashes)
List<Investment> all = investmentRepository.findByIpoIdAndStatus(ipoId, APPROVED);
// Memory: 5M * 1KB = 5GB → OutOfMemoryException
```

---

## Transactional Boundaries

### Strategy: Transaction per Batch

```java
@Transactional  // Opens transaction at batch start
private AllotmentBatchResult processAllotmentBatch(...) {
    // All operations within single transaction:
    // - Load investments (read)
    // - Create AllotmentLot entities (create)
    // - Update Investment status (update)
    // - Save to DB (commit on method exit)
    
    // Rollback if exception occurs
    // ✓ No partial allocations
    // ✓ Consistent state
}
```

**Benefits:**
```
✓ Atomicity: All-or-nothing per batch
✓ Consistency: No partial updates
✓ Isolation: Batch operations isolated from other batches
✓ Durability: Successfully committed batches persist

✓ Error recovery: Failed batch doesn't corrupt previous batches
✓ Progress tracking: Each committed batch = permanent progress
```

### Isolation Level

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public AllotmentResult performFairLotteryAllotment(...) {
    // REPEATABLE_READ:
    // - Prevents dirty reads (other transactions can't read uncommitted changes)
    // - Prevents non-repeatable reads (we always see same data within tx)
    // - Allows phantom reads (other tx can insert new rows)
    
    // Why not SERIALIZABLE?
    // - Much slower (locking overhead)
    // - Not necessary for allotment (read-once, write-once)
    // - Would serialize all batches (no parallelism benefit)
}
```

---

## Performance Analysis

### Scenario: 5 Million Applications

```
Setup:
  Total applications: 5,000,000
  Batch size: 10,000
  Total batches: 500
  
Batch processing time breakdown:
  Query (10K records):      ~50ms
  Generate lots:            ~100ms
  Calculate allocations:    ~150ms
  Create objects:           ~50ms
  Batch insert/update:      ~100ms
  Transaction commit:       ~50ms
  ───────────────────────────────
  Time per batch: ~500ms
  
Total time: 500 batches * 500ms = 250,000ms = 4.2 minutes
```

### With Optimizations

```
Same setup WITH optimizations:

Batch processing time (optimized):
  Query with index:         ~20ms   (-60%)
  Generate lots (cached):   ~50ms   (-50%)
  Calculate (vectorized):   ~75ms   (-50%)
  Create objects (pooled):  ~25ms   (-50%)
  Batch insert (tuned):     ~50ms   (-50%)
  Transaction (fast):       ~25ms   (-50%)
  ───────────────────────────────
  Time per batch: ~245ms
  
Total time: 500 batches * 245ms = 122,500ms = 2 minutes

Performance improvement: 2.1x faster
```

---

## Failure Handling

### Partial Failure Recovery

```
Scenario: Batch 250 fails (out of 500)

Before:    Batches 1-249 ✓ committed
           Batch 250 ✗ rolled back
           Batches 251-500 ⏸ never started

Result: 
  - Batches 1-249 persisted (safe)
  - Batch 250 state rolled back (no partial allocation)
  - Re-run: Skip batches 1-249, resume from batch 250
  
Code:
  if (batch.number < startFromBatch) continue;
  
  AllotmentBatchResult result = processBatch(...);
  if (result.failed) {
    log.error("Batch {} failed", batch.number);
    // Continue with next batch or exit?
    break;  // Fail fast
  }
```

### Resumable Allotment

```java
public AllotmentResult resumeAllotment(Long allotmentId, int startBatch) {
    Allotment allotment = findAllotmentById(allotmentId);
    
    if (allotment.getStatus() != FAILED) {
        throw new IllegalStateException("Can only resume FAILED allotments");
    }
    
    long processedSoFar = allotment.getTotalApplicationsProcessed();
    int lastSuccessfulBatch = (int) (processedSoFar / pageSize);
    int resumeFromBatch = Math.max(lastSuccessfulBatch, startBatch);
    
    // Resume from last successful batch
    allotment.setStatus(IN_PROGRESS);
    allotmentRepository.save(allotment);
    
    for (int page = resumeFromBatch; ...) {
        // Process remaining batches
    }
}
```

---

## Monitoring & Metrics

### Progress Tracking

```java
log.info("Allotment progress: {}/{} applications processed ({:.1f}%)",
    totalProcessed,
    totalApplications,
    (totalProcessed * 100.0 / totalApplications));
```

Output:
```
Allotment progress: 0/5000000 applications processed (0.0%)
Allotment progress: 10000/5000000 applications processed (0.2%)
Allotment progress: 20000/5000000 applications processed (0.4%)
...
Allotment progress: 4990000/5000000 applications processed (99.8%)
Allotment progress: 5000000/5000000 applications processed (100.0%)
```

### Key Metrics

```
Metric                      Target          Warning       Critical
───────────────────────────────────────────────────────────────────
Batch processing time       < 500ms         > 1s          > 5s
Memory usage                < 50% heap      > 75%         > 90%
DB connection pool usage    < 30%           > 60%         > 90%
Transaction duration        < 500ms         > 1s          > 5s
Allotment completion        < 30 min        > 1 hour      > 2 hours
Allocation ratio accuracy   100% ± 0.001%   ± 0.01%       ± 0.1%
```

### Dashboard Example

```
IPO: TechCorp IPO-2026
Allotment Status: IN_PROGRESS

Progress:      ████████░░░░░░░░░░░░░░░░ 40%
Elapsed:       2m 15s
Estimated total: 5m 40s
Remaining:     3m 25s

Batches processed: 400 / 1000
Current batch: 400 (40,000 - 50,000 applications)
Batch progress: Processing allocations...

Memory: 512 MB / 2 GB (25%)
DB connections: 5 / 50 (10%)
Allocation accuracy: 99.999%
```

---

## Tuning Checklist

### Before Allotment

- [ ] Verify batch size configured: `allotment.batch-size: 10000`
- [ ] Check DB connection pool size: `hikari.maximum-pool-size: 50`
- [ ] Confirm indexes exist: `idx_investment_ipo_status`, `idx_allot_lot_*`
- [ ] Increase heap if large dataset: `-Xmx4G`
- [ ] Disable caching during allotment: `spring.cache.enabled: false`

### During Allotment

- [ ] Monitor memory usage (target: < 50% heap)
- [ ] Check DB connection pool (target: < 30% utilized)
- [ ] Review batch processing times (target: < 1s)
- [ ] Watch for errors in logs

### After Allotment

- [ ] Verify total allocated = available shares
- [ ] Spot-check allocation percentages
- [ ] Validate no investor over-allocated
- [ ] Confirm all applications processed

---

## Scaling Beyond 10 Million

For very large datasets (10M+), consider:

1. **Horizontal Partitioning**
   - Shard data by investor_id
   - Process multiple partitions in parallel

2. **Separate Read DB**
   - Read-heavy query load on read replica
   - Write allotments to primary

3. **Cache Warmup**
   - Pre-load indexes into memory
   - Pin frequently accessed pages

4. **Async Processing**
   - Use Kafka for distributed batch processing
   - Multiple workers process partitions in parallel

5. **Approximate Algorithms**
   - Trade small accuracy for massive speed gains
   - E.g., process sample → estimate → exact allocation

---

## References

- Batch processing: Spring Batch documentation
- Database optimization: MySQL performance tuning
- Transaction isolation: ACID properties
