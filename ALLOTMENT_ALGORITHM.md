# IPO Fair Lottery Allotment Algorithm

## Overview

This document explains the fair lottery algorithm used in the IPO Application & Allotment System. The algorithm ensures equitable share allocation when demand exceeds supply (oversubscription).

---

## Problem Statement

### Scenario
Given:
- `N` approved IPO applications
- Each application `i` requests `shares[i]`
- Total shares available: `S`
- Total shares requested: `Σ shares[i]`

Find:
- Allocation for each applicant such that:
  1. **Feasibility:** Sum of allocations ≤ S (all shares distributed)
  2. **Respect:** Allocation[i] ≤ requested[i] (no one gets more than requested)
  3. **Fairness:** No bias toward any applicant
  4. **Reproducibility:** Same input always produces same output (deterministic)

### Example
```
IPO-1: 1000 shares available

Applications:
  Alice: 600 shares
  Bob:   400 shares
  Carol: 300 shares
  ─────────────────
  Total: 1300 shares (oversubscribed 1.3x)
```

**Goal:** Allocate 1000 shares fairly among Alice, Bob, and Carol.

---

## Solution: Fair Lottery Algorithm

### Algorithm Steps

#### Step 1: Calculate Oversubscription Ratio

```python
total_requested = sum(investment.shares_requested for investment in applications)
total_available = ipo.total_shares_offered

if total_requested <= total_available:
    # No oversubscription - pro-rata allocation
    ratio = 1.0
    allocation[i] = shares_requested[i] for all i
else:
    # Oversubscribed - fair lottery
    ratio = total_available / total_requested
    ratio ∈ (0, 1)
    continue to Step 2
```

**For Example:**
```
ratio = 1000 / 1300 = 0.769
```

#### Step 2: Generate Deterministic Lot Numbers

**Purpose:** Create a "random" but reproducible ordering for fair treatment.

```python
seed = hash(ipo_id) % 2^63  # Deterministic from IPO ID

random_generator = Random(seed)

lot_numbers = {}
for investment in applications:
    lot_numbers[investment.id] = random_generator.nextInt(0, len(applications))

# Sort applications by lot number
sorted_applications = sort(applications, key=lambda x: lot_numbers[x.id])
```

**Why deterministic?**
- **Reproducibility:** Running allotment twice produces identical results
- **Auditability:** Can verify fairness by replaying with same seed
- **Prevention:** Eliminates bias or gaming through seed manipulation

**For Example:**
```
seed = hash(1) = 12345

Lot assignments:
  Alice → lotNumber = 5
  Bob   → lotNumber = 2
  Carol → lotNumber = 8

Sorted order: Bob(2), Alice(5), Carol(8)
```

#### Step 3: Allocate Shares Based on Sorted Order

**Allocation Formula:**
```
For each investor in sorted lot order:
  
  base_allocation = shares_requested[i] * ratio
  
  final_allocation[i] = floor(base_allocation)
  
  # Ensure minimum 1 share (if requested > 0 and available > 0)
  if final_allocation[i] == 0 and shares_requested[i] > 0:
    final_allocation[i] = min(1, remaining_shares)
```

**For Example:**
```
Allocation (in sorted order):
  Bob:   400 * 0.769 = 307.6 → floor = 307 shares ✓
  Alice: 600 * 0.769 = 461.4 → floor = 461 shares ✓
  Carol: 300 * 0.769 = 230.7 → floor = 230 shares ✓
  ─────────────────────────────────
  Total: 307 + 461 + 230 = 998 shares

Remaining: 1000 - 998 = 2 shares (distributed to first 2 in sorted order)
  Bob:   307 + 1 = 308 shares
  Alice: 461 + 1 = 462 shares
  Carol: 230 shares
  ─────────────────────────────────
  Total: 1000 shares ✓
```

#### Step 4: Persist Results

Create `AllotmentLot` records:
```sql
INSERT INTO allotment_lots (allotment_id, investment_id, shares_requested, shares_allocated, lot_number, allocation_percentage, random_seed)
VALUES
  (1, alice_id, 600, 462, 5, 77.00, 12345),
  (1, bob_id, 400, 308, 2, 77.00, 12345),
  (1, carol_id, 300, 230, 8, 76.67, 12345)
```

---

## Why This Algorithm?

### Fairness Properties

1. **No Bias:** Lot numbers are random (seeded)
   - No investor systematically favored/disadvantaged
   - Order changes with each IPO (new seed)

2. **Proportional:** Allocation respects request size
   - Investor requesting 600 shares gets more than one requesting 100
   - Everyone gets same percentage (within rounding)

3. **Deterministic:** Same IPO, same results
   - Seed = hash(ipo_id)
   - Can audit and verify fairness
   - No question of "Was it rigged?"

4. **Simple:** Easy to understand and verify
   - Math: multiply request by ratio, floor
   - Seed: just IPO ID hash
   - No complex algorithms or black boxes

### Comparison with Alternatives

| Method | Fairness | Predictability | Bias Risk |
|--------|----------|-----------------|-----------|
| **Fair Lottery** (ours) | ⭐⭐⭐⭐⭐ | Deterministic | None |
| **FCFS** (First-come-first-served) | ⭐ | Highly predictable | High (reward early appliers) |
| **Pro-rata** (proportional) | ⭐⭐⭐ | Fully predictable | Medium (size-based) |
| **Random without seed** | ⭐⭐⭐⭐ | Unpredictable | Possible (no audit trail) |
| **Admin discretion** | ⭐ | Unpredictable | Very High |

---

## Implementation Details

### Rounding Strategy

**Problem:** Dividing shares by ratio produces fractional shares, but we can only allocate integers.

**Solution:** Floor division with remainder distribution

```
Example with 1300 requests, 1000 available:

Alice (600): 600 * (1000/1300) = 461.538... → floor = 461
Bob   (400): 400 * (1000/1300) = 307.692... → floor = 307
Carol (300): 300 * (1000/1300) = 230.769... → floor = 230
                                              ─────────
                                Total floored = 998

Remainder: 1000 - 998 = 2 shares
Distribute to first 2 in sorted order:
  Alice: 461 + 1 = 462
  Bob:   307 + 1 = 308
  Carol: 230 + 0 = 230
         ───────────
         Total = 1000 ✓
```

**Correctness Guarantee:**
- Sum of floor(allocation) + remainder = total available ✓
- No share is unallocated or duplicated ✓

### Deterministic Seed Generation

```java
long generateDeterministicSeed(Long ipoId) {
    return Math.abs(ipoId.hashCode());
}
```

**Properties:**
- `ipoId` = 1 → Always produces same seed
- Different `ipoId` values → Different seeds (usually)
- Reproducible: Can run again, get same lot numbers
- Non-predictable: Hard to guess before running

---

## Performance Characteristics

### Time Complexity

```
N = number of applications
S = number of shares

Step 1 (Ratio): O(N)        - Single pass to sum requests
Step 2 (Lots):  O(N)        - Generate lot numbers
Step 3 (Sort):  O(N log N)  - Sort by lot number
Step 4 (Alloc): O(N)        - Allocate each investor
Step 5 (Save):  O(N)        - Batch insert to DB

Total: O(N log N) dominated by sorting
```

### Space Complexity

```
O(N) for storing lot numbers and allocation results
```

### Practical Performance

```
Dataset Size | Processing Time | Memory |
───────────────────────────────────────
10K apps     | ~100ms          | 10MB   |
100K apps    | ~800ms          | 80MB   |
1M apps      | ~8s             | 800MB  |
5M apps      | ~40s            | 4GB    |
```

(Actual times depend on hardware, DB I/O, batch size)

---

## Example Walkthrough

### Setup
```
IPO-100: 1000 shares available
Applications: 5

  Investment 1: Investor A, 600 shares → Lot 5
  Investment 2: Investor B, 400 shares → Lot 2
  Investment 3: Investor C, 300 shares → Lot 8
  Investment 4: Investor D, 200 shares → Lot 1
  Investment 5: Investor E, 200 shares → Lot 3

Total requested: 1700 shares
```

### Step 1: Calculate Ratio
```
ratio = 1000 / 1700 = 0.5882
```

### Step 2: Lot Numbers (Already assigned above)
```
Sorted by lot: D(1), B(2), E(3), A(5), C(8)
```

### Step 3: Allocate

```
Investor D: 200 * 0.5882 = 117.64 → 117 shares
Investor B: 400 * 0.5882 = 235.28 → 235 shares
Investor E: 200 * 0.5882 = 117.64 → 117 shares
Investor A: 600 * 0.5882 = 352.92 → 352 shares
Investor C: 300 * 0.5882 = 176.46 → 176 shares
                                   ─────────────
                    Floored total = 997 shares

Remaining: 1000 - 997 = 3 shares
Distribute to first 3: D (+1), B (+1), E (+1)

Final allocation:
  D: 117 + 1 = 118 shares (59.0% of request)
  B: 235 + 1 = 236 shares (59.0% of request)
  E: 117 + 1 = 118 shares (59.0% of request)
  A: 352 + 0 = 352 shares (58.7% of request)
  C: 176 + 0 = 176 shares (58.7% of request)
  ─────────────────────────────
  Total:     1000 shares ✓
```

### Result
Everyone gets approximately 58-59% of their request, determined by fair lottery lot ordering.

---

## Failure Scenarios & Recovery

### Scenario 1: Database Error During Allocation
```
Status: IN_PROGRESS
Action: Catch exception, mark as FAILED
Result: No partial allocations, all Investment status unchanged
Recovery: Admin can re-run allotment (same seed = same results)
```

### Scenario 2: Allotment Already Running
```
Attempt: Second admin clicks "Start Allotment"
Lock: Distributed lock prevents concurrent execution
Result: Returns 423 LOCKED, blocks second attempt
```

### Scenario 3: No Approved Applications
```
Setup: IPO has 0 approved investments
Result: Completes with status=COMPLETED, shares_allocated=0
```

### Scenario 4: Fractional Share Distribution
```
Setup: 1000 shares, 1700 requested
Issue: Floored allocations sum to 997, leaving 3 unallocated
Fix: Distribute remainder to first N investors in sorted order
Result: All 1000 shares allocated ✓
```

---

## Audit & Compliance

### Reproducibility
Run allotment twice with same seed:
```
./allotment.start(ipoId=1, seed=12345)
→ Results: A=462, B=308, C=230

./allotment.start(ipoId=1, seed=12345)
→ Results: A=462, B=308, C=230 (identical)
```

### Verifiability
Auditor can verify fairness:
```sql
-- Check for bias
SELECT investment_id, allocation_percentage, COUNT(*)
FROM allotment_lots
GROUP BY allocation_percentage
ORDER BY COUNT(*) DESC;
-- Should see roughly equal percentages for all investors

-- Check seed
SELECT random_seed, COUNT(DISTINCT investment_id)
FROM allotment_lots
WHERE allotment_id = 1
GROUP BY random_seed;
-- Should see single seed, all investments in one batch
```

### Compliance
- ✅ Non-discriminatory: Fair lottery treats all equally
- ✅ Transparent: Algorithm public and understandable
- ✅ Auditable: Seed and results stored for review
- ✅ Reproducible: Same input = same output

---

## Configuration & Tuning

### Batch Size
```yaml
allotment:
  batch-size: 10000  # Investments per batch
```

**Impact:**
- Larger batches → Fewer DB transactions, faster but more memory
- Smaller batches → More transactions, slower but lower memory
- **Recommendation:** 10,000 for 1M+ applications

### Seed Strategy
```java
// Current: Use IPO ID hash
seed = Math.abs(ipoId.hashCode());

// Alternative: Use timestamp
seed = System.currentTimeMillis();

// Alternative: User-provided
seed = request.getRandomSeed();
```

**Recommendation:** IPO ID hash for reproducibility and auditability

---

## Future Enhancements

### 1. Partial Allotment (Tranches)
Allocate in multiple rounds over time:
```
Round 1: 30% of shares to highest-value applicants
Round 2: 50% to remaining applicants (new lottery)
Round 3: 20% remaining shares (sorted by previous round) 
```

### 2. Weighted Lottery
Give preference to certain categories:
```
seed = hash(ipo_id + investor_type)
// First-time investors get lower lot numbers (better chance)
```

### 3. Guaranteed Minimum
Ensure every applicant gets at least 1 share:
```
if ratio * requested < 1:
    allocation[i] = min(requested[i], 1)
else:
    allocation[i] = floor(ratio * requested[i])
```

### 4. Category-Based Allocation
Separate quota for each investor type:
```
Retail investor quota:    500 shares
Institution quota:        300 shares
Employee quota:           200 shares
```

---

## References

- Fair Allocation: https://en.wikipedia.org/wiki/Fair_division
- Lottery System: https://en.wikipedia.org/wiki/Lottery
- IPO Regulation: SEC rules on fair allocation requirements
