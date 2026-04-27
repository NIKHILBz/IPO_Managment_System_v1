# Testing Guide - IPO Application Concurrency

## How to Test the Concurrency Control

This guide walks through testing the duplicate prevention and locking mechanisms locally.

---

## Prerequisites

1. MySQL server running on `localhost:3306`
2. Redis server running on `localhost:6379`
3. Project built: `mvn clean install`
4. Application running: `mvn spring-boot:run -pl ipo-app`

---

## Test 1: Database Unique Constraint Validation

### Objective
Verify that the database enforces `(ipo_id, investor_id)` uniqueness.

### Steps

1. **Start application:**
   ```bash
   mvn spring-boot:run -pl ipo-app
   ```

2. **Create test data via API:**
   ```bash
   # Create Company
   curl -X POST http://localhost:8080/api/v1/companies \
     -H "Content-Type: application/json" \
     -d '{
       "companyName": "TechCorp",
       "industry": "Technology",
       "description": "A tech company",
       "foundedYear": 2020,
       "ceoName": "John Doe",
       "headquarters": "San Francisco",
       "currentValuation": 1000000000
     }'
   # Note: Returns company ID (e.g., 1)

   # Create IPO (save ID from response)
   curl -X POST http://localhost:8080/api/v1/ipos \
     -H "Content-Type: application/json" \
     -d '{
       "companyId": 1,
       "pricePerShare": 100.50,
       "totalShares": 50000,
       "openDate": "2026-05-01T09:00:00",
       "closeDate": "2026-05-10T15:00:00"
     }'
   # IPO ID: 1

   # Create Investor (save ID from response)
   curl -X POST http://localhost:8080/api/v1/investors \
     -H "Content-Type: application/json" \
     -d '{
       "name": "John Investor",
       "email": "john@investor.com",
       "pan": "ABCDE1234F",
       "totalInvestment": 500000,
       "verified": true
     }'
   # Investor ID: 1
   ```

3. **Submit first application (succeeds):**
   ```bash
   curl -X POST http://localhost:8080/api/v1/applications \
     -H "Content-Type: application/json" \
     -d '{
       "ipoId": 1,
       "investorId": 1
     }'
   # Response: 201 CREATED
   # {"id": 1, "applicationNumber": "APP-1234567890-ABCD", "status": "SUBMITTED"}
   ```

4. **Submit second application (duplicate - should fail):**
   ```bash
   curl -X POST http://localhost:8080/api/v1/applications \
     -H "Content-Type: application/json" \
     -d '{
       "ipoId": 1,
       "investorId": 1
     }'
   # Response: 400 BAD REQUEST
   # {"error": "Duplicate application already exists"}
   ```

### Expected Result
First request succeeds (201). Second request fails (400) with unique constraint violation caught and handled gracefully.

---

## Test 2: Distributed Lock (Redis)

### Objective
Verify that Redis distributed lock prevents concurrent submissions.

### Steps

1. **Terminal 1 - Start blocking operation:**
   ```bash
   # Add logging to see lock acquisition
   # Edit ApplicationService.java, ensure logging is DEBUG level
   
   # Manually trigger long-running submit with debug
   curl -X POST http://localhost:8080/api/v1/applications \
     -H "Content-Type: application/json" \
     -d '{
       "ipoId": 2,
       "investorId": 2
     }'
   # Check logs: "Distributed lock acquired for application submission"
   ```

2. **Terminal 2 - Submit duplicate while Terminal 1 is processing (within 30s):**
   ```bash
   curl -X POST http://localhost:8080/api/v1/applications \
     -H "Content-Type: application/json" \
     -d '{
       "ipoId": 2,
       "investorId": 2
     }'
   ```

### Expected Result
Terminal 2 request fails immediately with message: `"Application submission in progress. Please try again later."` (409 Conflict)

### Check Redis Lock
```bash
redis-cli
> KEYS "lock:*"
1) "lock:app:ipo:2:investor:2"
> GET "lock:app:ipo:2:investor:2"
"<uuid-token>"
> TTL "lock:app:ipo:2:investor:2"
(integer) 25  # Expires in 25 seconds
```

---

## Test 3: Pessimistic Locking

### Objective
Verify that `FOR UPDATE` lock prevents race conditions.

### Steps

1. **Enable query logging in MySQL:**
   ```sql
   SET SESSION sql_mode='';
   SET SESSION general_log = 'ON';
   SET SESSION log_output = 'TABLE';
   ```

2. **Submit two applications sequentially:**
   ```bash
   # Both for different IPOs/investors (to avoid unique constraint)
   for i in {3..4}; do
     curl -X POST http://localhost:8080/api/v1/applications \
       -H "Content-Type: application/json" \
       -d "{
         \"ipoId\": $i,
         \"investorId\": $i
       }"
   done
   ```

3. **Check query log for `FOR UPDATE`:**
   ```sql
   SELECT event_time, argument FROM mysql.general_log 
   WHERE argument LIKE '%FOR UPDATE%'
   ORDER BY event_time DESC LIMIT 5;
   ```

### Expected Result
Queries show `SELECT ... WHERE ipo_id=? AND investor_id=? FOR UPDATE` indicating row-level locking.

---

## Test 4: Optimistic Locking Retry

### Objective
Verify optimistic locking with retry on version conflict.

### Steps

1. **Get existing application and version:**
   ```bash
   curl http://localhost:8080/api/v1/applications/1
   # Response includes: "version": 0
   ```

2. **Simulate version conflict:**
   - Edit `ApplicationService.approveApplication()` temporarily to add:
   ```java
   throw new ObjectOptimisticLockingFailureException("Simulated conflict", new Object());
   ```

3. **Try to approve (should retry):**
   ```bash
   curl -X PATCH http://localhost:8080/api/v1/applications/1/approve
   # Logs should show: "Optimistic lock conflict detected, retrying (1/3)"
   ```

4. **Revert the temporary exception and test again:**
   ```bash
   curl -X PATCH http://localhost:8080/api/v1/applications/1/approve
   # Response: 200 OK
   # Logs show successful approval without conflict
   ```

### Expected Result
First test shows retry log messages. Second test shows clean approval without retries.

---

## Test 5: Concurrent Submissions (Load Test)

### Objective
Simulate multiple concurrent submission attempts.

### Steps

1. **Create test script (concurrent_test.sh):**
   ```bash
   #!/bin/bash
   
   IPO_ID=5
   INVESTOR_ID=5
   CONCURRENT_REQUESTS=10
   
   echo "Submitting $CONCURRENT_REQUESTS concurrent requests..."
   
   for i in $(seq 1 $CONCURRENT_REQUESTS); do
     {
       curl -s -X POST http://localhost:8080/api/v1/applications \
         -H "Content-Type: application/json" \
         -d "{
           \"ipoId\": $IPO_ID,
           \"investorId\": $INVESTOR_ID
         }" | jq '.'
     } &
   done
   
   wait
   echo "All requests completed"
   ```

2. **Run the test:**
   ```bash
   chmod +x concurrent_test.sh
   ./concurrent_test.sh
   ```

3. **Check results:**
   ```bash
   # 1 success (201 CREATED)
   # 9 failures (400 or 409)
   ```

### Expected Result
- **1 application successfully created** (201 CREATED)
- **9 requests fail** with either:
  - 409 Conflict (distributed lock failure)
  - 400 Bad Request (duplicate constraint)

### Verify with API
```bash
curl http://localhost:8080/api/v1/applications/ipo/5
# Returns 1 application total (all 10 attempts created only 1 app)
```

---

## Test 6: Database Locking Monitoring

### Objective
Monitor active database locks during concurrent operations.

### Steps

1. **Terminal 1 - Start monitoring locks:**
   ```sql
   -- In MySQL terminal
   SELECT * FROM INFORMATION_SCHEMA.INNODB_LOCKS;
   SELECT * FROM INFORMATION_SCHEMA.INNODB_LOCK_WAITS;
   ```

2. **Terminal 2 - Run concurrent test:**
   ```bash
   ./concurrent_test.sh
   ```

3. **Check lock statistics (while running):**
   ```sql
   SHOW ENGINE INNODB STATUS \G | grep -A 20 "LOCK WAIT"
   ```

### Expected Result
- Locks briefly appear during submission
- No deadlocks (lock count returns to 0)
- All locks released after transaction completes

---

## Test 7: Redis Failure Scenario

### Objective
Verify graceful degradation if Redis is unavailable.

### Steps

1. **Stop Redis:**
   ```bash
   redis-cli shutdown
   ```

2. **Try to submit application:**
   ```bash
   curl -X POST http://localhost:8080/api/v1/applications \
     -H "Content-Type: application/json" \
     -d '{
       "ipoId": 6,
       "investorId": 6
     }'
   # Response: 500 INTERNAL_SERVER_ERROR
   # Logs: "Error acquiring lock: Connection refused"
   ```

3. **Restart Redis:**
   ```bash
   redis-server
   ```

4. **Verify recovery:**
   ```bash
   # Same request should now succeed
   curl -X POST http://localhost:8080/api/v1/applications \
     -H "Content-Type: application/json" \
     -d '{
       "ipoId": 7,
       "investorId": 7
     }'
   # Response: 201 CREATED
   ```

### Expected Result
- Application fails gracefully when Redis unavailable
- Recovers automatically when Redis is back online

---

## Test 8: Transaction Isolation Level

### Objective
Verify SERIALIZABLE isolation for submissions.

### Steps

1. **Enable transaction monitoring:**
   ```sql
   SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;
   ```

2. **Monitor transaction behavior:**
   ```sql
   SELECT * FROM INFORMATION_SCHEMA.INNODB_TRX;
   SELECT * FROM INFORMATION_SCHEMA.INNODB_LOCKS;
   ```

3. **Run concurrent submissions:**
   ```bash
   ./concurrent_test.sh
   ```

4. **Check isolation levels in logs:**
   ```bash
   grep -i "isolation\|serializable" logs/application.log
   ```

### Expected Result
All transactions use SERIALIZABLE isolation level, preventing phantom reads and dirty reads.

---

## Monitoring Commands

### Redis Lock Status
```bash
redis-cli
> INFO stats        # Connection count, commands/sec
> MONITOR           # Real-time command trace
> CLIENT LIST       # Connected clients
```

### MySQL Lock Status
```sql
-- Active locks
SELECT * FROM INFORMATION_SCHEMA.INNODB_LOCKS;

-- Waiting locks
SELECT * FROM INFORMATION_SCHEMA.INNODB_LOCK_WAITS;

-- Transaction status
SELECT * FROM INFORMATION_SCHEMA.INNODB_TRX;

-- Thread list
SHOW PROCESSLIST;
```

### Application Logs
```bash
# Watch logs in real-time
tail -f logs/application.log | grep -i "lock\|duplicate\|concurrency"
```

---

## Performance Benchmarking

### Single Request Latency
```bash
# Time a single submission
time curl -X POST http://localhost:8080/api/v1/applications \
  -H "Content-Type: application/json" \
  -d '{
    "ipoId": 8,
    "investorId": 8
  }' -o /dev/null -s -w "\nTotal: %{time_total}s\n"
```

**Expected:** 50-150ms total (including distributed lock overhead)

### Throughput
```bash
# Apache Bench - 100 requests, 10 concurrent
ab -n 100 -c 10 \
  -p payload.json \
  -T application/json \
  http://localhost:8080/api/v1/applications

# Expected: 100-300 req/sec (limited by Redis lock contention)
```

### Comparison Without Locking
Remove distributed lock call temporarily:
- **With locking:** 150 req/sec
- **Without locking (but with pessimistic lock):** 500 req/sec
- **Without locking (no pessimistic lock):** 1000 req/sec (but allows duplicates!)

---

## Troubleshooting

### Issue: "Connection refused" on Redis
```
Symptom: All submissions fail with 500 error
Solution:
  1. Check Redis running: redis-cli ping
  2. Check port: ps aux | grep redis
  3. Start Redis: redis-server
```

### Issue: Optimistic Lock Retries Exhausted
```
Symptom: "ObjectOptimisticLockingFailureException after 3 attempts"
Diagnosis:
  1. Check if same application being updated by many threads
  2. Increase MAX_RETRY_ATTEMPTS in ApplicationService
  3. Increase backoff sleep time
  4. Add more application processing parallelism
```

### Issue: Deadlock Detected
```
Symptom: "Deadlock found when trying to get lock"
Diagnosis:
  1. Check transaction locks: SHOW ENGINE INNODB STATUS
  2. Verify lock order is consistent
  3. Check for circular waits
```

---

## Success Criteria

✅ **Test 1:** Unique constraint prevents duplicates
✅ **Test 2:** Distributed lock blocks concurrent submissions
✅ **Test 3:** Pessimistic locking visible in query log
✅ **Test 4:** Optimistic locking retries on conflict
✅ **Test 5:** Concurrent load creates only 1 application
✅ **Test 6:** No deadlocks during concurrent access
✅ **Test 7:** Graceful degradation without Redis
✅ **Test 8:** SERIALIZABLE isolation enforced

When all tests pass, the concurrency control implementation is validated! ✅
