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
- **COSE and Base45:** For encoding and decoding operations.

## Dependencies

- **Spring Boot Starter Web:** For web development.
- **Spring Boot Starter Data JPA:** For JPA data access.
- **Spring Boot Starter Test:** For testing purposes.
- **com.upokecenter CBOR (via COSE):** For CBOR encoding/decoding.
- **Lombok:** For reducing boilerplate code.
- **AWS SDK for Java v2 (S3):** For S3 file storage.
- **PostgreSQL Driver:** For PostgreSQL database connectivity.
- **ModelMapper:** For object mapping.
- **Base45:** For Base45 encoding/decoding.
- **COSE:** For COSE operations.
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
- `net.i2p.crypto:eddsa` is excluded from `cose-java` (CVE-2020-36843, no fix published). Ed25519-signed COSE messages are therefore not supported; ES256 (ECDSA) is.
- Building on JDK 23+ works because Lombok is configured explicitly as an annotation processor.
