# AML Application

A Java 17 and Spring Boot service for account authentication, user administration, compliance report management, KYC records, and PDF exports.

## Technology

- Java 17 and Spring Boot
- Spring MVC, Spring Security, Spring Data JPA, and Hibernate
- MySQL persistence
- JWT authentication and BCrypt password encoding
- Apache POI for spreadsheet imports and Apache PDFBox for PDF handling

## Requirements and configuration

- Java 17
- Maven
- A MySQL server reachable at the configured datasource URL
- Mail server settings for registration and password recovery emails

Configure database and mail credentials outside source control using environment variables or deployment-specific configuration. Set `JWT_SECRET` to a private random value containing at least 32 UTF-8 bytes. The checked-in development fallback secret is not suitable for production.

Database connection, Hibernate schema behavior, server port, email URLs, JWT expiration, and rate limits are configured in `src/main/resources/application.properties`. The SQL schema reference is `src/main/resources/schema.sql`.

## Build and run

From the repository root:

```sh
mvn clean package
mvn spring-boot:run
```

Run the existing tests with:

```sh
mvn test
```

The application is configured to listen on port `8080` by default. API documentation, when the service starts successfully, is available at `/swagger-ui/index.html`.

## API overview

| Base path | Purpose | Access |
| --- | --- | --- |
| `/api/auth` | Registration, login, initial password setup, and password reset | Public endpoints; protected actions require their one-time tokens |
| `/api/users` | User lookup, profile updates, and spreadsheet-based bulk registration | Administrator role |
| `/api/v1/compliance` | Create, read, update, and delete compliance reports | Administrator role |
| `/api/pdf` | PDF text extraction and compliance/KYC report exports | Administrator role |

Protected endpoints require an `Authorization: Bearer <token>` header. Exact paths and request payloads are defined by the controllers and can be inspected through the OpenAPI documentation when the service is available.

## Project updates

- Removed the separate CompanyDetail persistence model and its DTOs, along with CompanyDetail mapping and update logic in user and authentication flows.
- User registration and bulk import now use the existing company-name property directly on the user model; the removed CompanyDetail fields are no longer part of user registration, update, login, or response payloads.
- Removed the CompanyDetail table and its foreign-key relationship from the SQL schema definition.
- Added a root `.gitignore` rule for `/target/`, so local Maven build output is excluded from future Git adds.

## Known startup issue

The last observed application startup log reports a Hibernate `DuplicateMappingException`: both `org.aml.entity.User` and `org.aml.model.User` are JPA entities with the default entity name `User`. Resolve the duplicate entity mapping before expecting the application context to start. A successful Java compile does not detect this runtime JPA mapping conflict.
