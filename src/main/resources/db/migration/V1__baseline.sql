-- Baseline schema (generated from the JPA entities with Hibernate 7 against PostgreSQL 18).
-- Databases created earlier by hibernate ddl-auto=update are baselined at version 1 and skip this script
-- (spring.flyway.baseline-on-migrate=true, baseline-version=1); new databases run it.

create table ips_file (
    is_accessed boolean default false,
    created_at timestamp(6) not null,
    id varchar(255) not null,
    manifest_id varchar(255) not null,
    primary key (id)
);

create table qr_code (
    created_at timestamp(6) not null,
    expires_on timestamp(6),
    id varchar(36) not null,
    country_code varchar(255),
    flag varchar(255),
    json_url varchar(255),
    key varchar(255),
    manifest_id varchar(255) not null,
    pass_code varchar(255),
    primary key (id)
);
