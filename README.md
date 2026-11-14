# entomo GDHCN Validator

## Overview

The **entomo GDHCN Validator** is a microservice designed for validating Verifiable Smart Health Certificate (VSHC). This application uses Spring Boot and various dependencies to perform certificate validation and related operations.

## Features

- **GDHCN Certificate Issuance:** Endpoint to issue certificates based on QR code requests.
- **GDHCN Certificate Validation:** Endpoint to validate certificates.
- **Manifest Retrieval:** Endpoint to retrieve manifests in JSON format.
- **File Operations:** Upload and download files to/from AWS S3 bucket.
- **Certificate and Key Management:** Handles private keys and certificates for secure communications.

## Technologies

- **Spring Boot 4.1:** Framework for building and running the application.
- **Java 21 (LTS):** Programming language used for development.
- **PostgreSQL:** Database for storing application data.
- **AWS SDK for Java v2:** Interact with Amazon S3.
- **ModelMapper:** Object mapping for data transformation.
- **java.net.http.HttpClient:** HTTP client (mutual TLS to the GDHCN trust network).
- **COSE (in-house) and Base45:** For encoding and decoding operations.

## Dependencies

- **Spring Boot Starter Web:** For web development.
- **Spring Boot Starter Data JPA:** For JPA data access.
- **Spring Boot Starter Test:** For testing purposes.
- **com.upokecenter CBOR:** For CBOR encoding/decoding.
- **Lombok:** For reducing boilerplate code.
- **AWS SDK for Java v2 (S3):** For S3 file storage.
- **PostgreSQL Driver:** For PostgreSQL database connectivity.
- **ModelMapper:** For object mapping.
- **Base45:** For Base45 encoding/decoding.
- **COSE_Sign1 (in-house, `hcert.CoseSign1`):** For COSE operations.
- **Commons Compress:** For file compression operations.

## Configuration

Ensure you configure the following properties in your `application.properties` or `application.yml`:

```properties
# IPS SHLink Expiry time
ips.shlink.expiry = your-no-of-minutes
# Profile Configuration, for Local use 'fileSystem', for AWS use 'aws'
spring.profiles.active = fileSystem

# Spring JPA Configuration
spring.datasource.url=your-database-url
spring.datasource.username=your-database-username
spring.datasource.password=yourdatabase-password

# AWS S3 configuration
aws.bucket.s3.bucket.name=your-bucket-name
aws.bucket.access.key=your-access-key
aws.bucket.access.secret=your-secret-key

# TLS configuration
tng.tls.pem=path/to/your/certificate.pem
tng.tls.key=path/to/your/private-key.pem
tng.country = XA
tng.dsc.privateKey =path/to/your/dsc-privaye-key
tng.dsc.privateKey.kid =dsc kid id


```
## Build and Run

To build and run the application, follow these steps:

### 1. Clone the repository:

```properties
git clone https://github.com/gdhcnentomo/entomo-gdhcn-validator.git
cd entomo-gdhcn-validator
```
### 2. Build the project using Maven:
```properties
mvn clean install
```
### 3. Run the application:
```properties
mvn spring-boot:run
```

## API Endpoints

- POST /v2/vshcIssuance: Issues a GDHCN certificate.
- POST /v2/vshcValidation: Validates a GDHCN certificate.
- GET /v2/ips-json/{jsonId}: Retrieves JSON data for the given ID.
- POST /v2/manifests/{jsonId}: Retrieves a manifest for the given ID.

## Exception Handling

- **GdhcnValidationException:** Handles validation exceptions and returns an appropriate error response.
- **MethodArgumentNotValidException:** Handles validation errors in request arguments.

## License

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0


## Contact
For any questions or support, please reach out to <a href="">gdhcn@entomo.co</a>


### Notes:
```properties
- Replace placeholders such as `your-database-url`,`your-database-usernmae`,`your-database-password`,`your-bucket-name`, `your-access-key` and `your-secret-key` with actual values relevant to your setup.
- Update the repository URL with the actual URL where the code is hosted.
- Adjust any configuration or details based on your actual implementation and environment.
```

## Acknowledgements

- **HCERT Java Library:** This project utilizes the HCERT Java library for handling health certificate operations. Special thanks to the developers of HCERT Java for their valuable contribution.

## Security maintenance

- Dependabot (Maven and GitHub Actions) and a weekly OSV scan run in CI.
- COSE_Sign1 is implemented in-house (`hcert.CoseSign1`) on the maintained `com.upokecenter:cbor` library. It signs with ES256 and verifies ES256 and PS256 only; Ed25519/EdDSA is intentionally unsupported. `cose-java` is kept as a test-scope cross-check.
- Building on JDK 23+ works because Lombok is configured explicitly as an annotation processor.

## Try it (no configuration)

```bash
docker compose up --build
```

This starts PostgreSQL, generates throwaway keys, runs a mock of the WHO trust list and starts the validator on
http://localhost:8082. In another terminal, `sh docker/demo/smoke.sh` issues a certificate, validates it through all nine
steps, downloads the shared link and checks that a tampered code is rejected. Stop and discard everything with
`docker compose down -v`. The demo is for evaluation only: its keys are generated locally, its database password is fixed and the
trust list is a stand-in for the real WHO TNG. Use the configuration below for anything else.

## Docker

```bash
docker build -t gdhcn-validator .
docker run --rm -p 8082:8082 -v /path/to/secrets:/secrets:ro   -e SPRING_PROFILES_ACTIVE=fileSystem   -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/gdhcn   -e SPRING_DATASOURCE_USERNAME=... -e SPRING_DATASOURCE_PASSWORD=...   -e TNG_TLS_PEM=/secrets/tls.pem -e TNG_TLS_KEY=/secrets/tls.key   -e TNG_DSC_PRIVATEKEY=/secrets/dsc.key -e TNG_DSC_PRIVATEKEY_KID=<kid> -e TNG_COUNTRY=<ISO code>   gdhcn-validator
```

The image is multi-stage (Maven build, Temurin 21 JRE on Alpine), runs as a non-root user and listens on 8082. All settings
are read from environment variables (Spring relaxed binding). Keys and certificates are mounted, never baked in
(`.dockerignore` excludes `*.pem` / `*.key`). With the `fileSystem` profile, mount a volume on `/data` to keep issued IPS files.
Flyway creates or baselines the schema on start. There is no health endpoint yet, so use a TCP check on port 8082 for probes.

## Database schema

The schema is owned by Flyway (`src/main/resources/db/migration`); Hibernate only validates it (`ddl-auto=validate`).
Databases that were created by the earlier `ddl-auto=update` setup are baselined at version 1 on first start
(`spring.flyway.baseline-on-migrate=true`), so no data is touched. Add schema changes as new `V<n>__*.sql` files; never edit an applied one.
`V2` builds the `manifest_id` indexes with `CREATE INDEX CONCURRENTLY` (no write lock), which is why `spring.flyway.postgresql.transactional-lock=false` is set; keep it set if you migrate with your own Flyway setup.

## S3 storage

With the `aws` profile, files go to the configured bucket. Set `aws.bucket.endpoint` to use an S3-compatible
server (MinIO, S3Mock, ...), and `aws.bucket.region` (default `ap-southeast-1`). If `aws.bucket.access.key` is empty
the default AWS credential provider chain is used.

## Tests

`./mvnw verify` runs unit tests, an issue -> validate round trip over the REST API, and integration tests against
real PostgreSQL and S3Mock containers. The container tests are skipped automatically when Docker is not available.
