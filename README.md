# IPO Application

A multi-module Spring Boot application for IPO (Initial Public Offering) management.

## Project Structure

```
ipo-application/
├── ipo-common/          # Common utilities and constants
├── ipo-entity/          # Entity models and JPA repositories
├── ipo-service/         # Business logic service layer
└── ipo-app/             # Main Spring Boot application
```

## Modules

### ipo-common
- Shared utilities and constants across all modules

### ipo-entity
- JPA entity models
- Database repositories
- Hibernate configuration

### ipo-service
- Business logic implementation
- Redis caching configuration
- Kafka producer/consumer
- Service layer

### ipo-app
- Main Spring Boot application
- REST controllers
- Application entry point

## Technology Stack

- **Java 11**
- **Spring Boot 2.7.14**
- **Spring Data JPA**
- **Redis** (Caching)
- **Kafka** (Message Streaming)
- **H2 Database** (In-memory for testing)
- **Lombok** (Reduce boilerplate)

## Building

```bash
mvn clean install
```

## Running

```bash
mvn spring-boot:run -pl ipo-app
```

## Configuration

Edit `ipo-app/src/main/resources/application.yml` for:
- Database configuration
- Redis settings
- Kafka broker settings
- Server port

## Requirements

- Java 11+
- Maven 3.6+
- Redis server (optional, for caching)
- Kafka broker (optional, for messaging)
