# IPO Application - Setup and Usage Guide

## Prerequisites

- Java 11 or higher
- Maven 3.6 or higher
- Redis server (optional, for caching)
- Kafka broker (optional, for messaging)
- MySQL/PostgreSQL (optional, can use H2 in-memory for testing)

## Project Setup

### 1. Clone and Build

```bash
cd ipo-application
mvn clean install
```

### 2. Database Setup

The application uses H2 in-memory database by default. For production:

Update `ipo-app/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ipo_db
    username: root
    password: your_password
    driverClassName: com.mysql.cj.jdbc.Driver
```

### 3. Redis Setup

To enable Redis caching:

```bash
# Start Redis server
redis-server
```

Update `application.yml`:
```yaml
spring:
  redis:
    host: localhost
    port: 6379
```

### 4. Kafka Setup

To enable Kafka messaging:

```bash
# Start Kafka
bin/kafka-server-start.sh config/server.properties
```

## Running the Application

### Development Mode

```bash
mvn spring-boot:run -pl ipo-app
```

The application will start on `http://localhost:8080`

### Production Mode

```bash
mvn clean package -pl ipo-app
java -jar ipo-app/target/ipo-app-1.0.0.jar
```

## API Endpoints

### Health Check
```
GET /api/v1/health
GET /api/v1/health/ready
```

### Company Endpoints
```
GET    /api/v1/companies           - Get all companies
GET    /api/v1/companies/{id}      - Get company by ID
POST   /api/v1/companies           - Create new company
PUT    /api/v1/companies/{id}      - Update company
DELETE /api/v1/companies/{id}      - Delete company
```

### IPO Endpoints
```
GET    /api/v1/ipos                - Get all IPOs
GET    /api/v1/ipos/{id}           - Get IPO by ID
GET    /api/v1/ipos/status/{status}- Get IPOs by status
POST   /api/v1/ipos                - Create new IPO
PUT    /api/v1/ipos/{id}           - Update IPO
PATCH  /api/v1/ipos/{id}/status    - Update IPO status
DELETE /api/v1/ipos/{id}           - Delete IPO
```

### Investor Endpoints
```
GET    /api/v1/investors                - Get all investors
GET    /api/v1/investors/{id}           - Get investor by ID
GET    /api/v1/investors/email/{email}  - Get investor by email
POST   /api/v1/investors                - Create new investor
PUT    /api/v1/investors/{id}           - Update investor
PATCH  /api/v1/investors/{id}/verify    - Verify investor
DELETE /api/v1/investors/{id}           - Delete investor
```

### Investment Endpoints
```
GET    /api/v1/investments              - Get all investments
GET    /api/v1/investments/{id}         - Get investment by ID
GET    /api/v1/investments/ipo/{ipoId}  - Get investments by IPO
GET    /api/v1/investments/investor/{investorId} - Get investments by investor
POST   /api/v1/investments              - Create new investment
PUT    /api/v1/investments/{id}         - Update investment
PATCH  /api/v1/investments/{id}/approve - Approve investment
PATCH  /api/v1/investments/{id}/reject  - Reject investment
PATCH  /api/v1/investments/{id}/allocate?shares={count} - Allocate shares
DELETE /api/v1/investments/{id}         - Delete investment
```

### Application Form Endpoints
```
GET    /api/v1/applications                          - Get all applications
GET    /api/v1/applications/{id}                     - Get application by ID
GET    /api/v1/applications/number/{applicationNumber} - Get by application number
GET    /api/v1/applications/ipo/{ipoId}              - Get applications by IPO
GET    /api/v1/applications/investor/{investorId}    - Get applications by investor
POST   /api/v1/applications                          - Submit new application
PATCH  /api/v1/applications/{id}/approve             - Approve application
PATCH  /api/v1/applications/{id}/reject?reason={reason} - Reject application
PATCH  /api/v1/applications/{id}/status?status={status} - Update status
DELETE /api/v1/applications/{id}                     - Delete application
```

## Configuration

### Cache Configuration
Edit `cache` section in `application.yml`:

```yaml
cache:
  enabled: true
  default-ttl: 10        # minutes
  short-ttl: 5
  long-ttl: 60
  cache-null-values: false
  policies:
    company:
      ttl: 30
      cache-null: false
    ipo:
      ttl: 20
```

### Logging Configuration
```yaml
logging:
  level:
    root: INFO
    com.ipo: DEBUG
```

## Testing

### Run All Tests
```bash
mvn test
```

### Run Specific Test Class
```bash
mvn test -Dtest=CompanyServiceTest
```

### Run with Coverage
```bash
mvn test jacoco:report
```

## Architecture

### Modules
- **ipo-common**: Shared utilities
- **ipo-entity**: JPA entities and repositories
- **ipo-service**: Business logic and services
- **ipo-app**: Spring Boot application and REST controllers

### Key Features
1. **Caching**: Redis-based caching with configurable TTL
2. **Event Streaming**: Kafka producer/consumer for async processing
3. **Concurrency Control**: ReadWriteLock for thread-safe operations
4. **Transaction Management**: Spring @Transactional for data consistency
5. **REST API**: Comprehensive REST endpoints
6. **Health Checks**: Built-in health monitoring

## Monitoring

### Health Endpoint
```
GET /actuator/health
```

### Metrics
```
GET /actuator/metrics
```

### Active Locks (Concurrency)
Monitor lock count via ConcurrentOperationService:
- `getActiveLockCount()` - Returns number of active locks
- `clearAllLocks()` - Clears all locks (use with caution)

## Troubleshooting

### Redis Connection Issues
```
Check Redis is running: redis-cli ping
Expected output: PONG
```

### Kafka Connection Issues
```
Check Kafka broker is running
Check bootstrap-servers configuration in application.yml
```

### Database Initialization
```
If tables not created:
- Check spring.jpa.hibernate.ddl-auto=update in application.yml
- Verify database connectivity
```

## Performance Tips

1. **Enable Redis Caching** for frequently accessed data
2. **Use Kafka** for async event processing
3. **Configure Connection Pooling** for better database performance
4. **Monitor Lock Contention** for concurrent operations
5. **Use Pagination** for large data sets

## Security Considerations

1. Validate all input data
2. Use authentication/authorization (future enhancement)
3. Encrypt sensitive data in transit (HTTPS)
4. Implement rate limiting for API endpoints
5. Use database transactions for data integrity

## Deployment

### Docker Support
Create a Dockerfile for containerization:
```dockerfile
FROM openjdk:11-jre-slim
COPY ipo-app/target/ipo-app-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Kubernetes Support
Use standard Spring Boot deployment practices with Kubernetes manifests.

## Support and Documentation

For more details on specific modules, see README.md in each module directory.
