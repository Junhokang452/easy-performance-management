# easy-performance-management backend framework migration

Date: 2026-09-07 KST

## Applied baseline

- Java 21 retained.
- Spring Boot upgraded from 3.4.5 to 4.1.1.
- Gradle wrapper upgraded from 8.10.2 to 8.14.5 using the canonical core wrapper files.
- `easy-platform-core` composite coordinate upgraded to `1.0.0-SNAPSHOT`.
- Springdoc upgraded from 2.7.0 to 3.1.1.

The wrapper binary SHA-256 is
`7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172`.
The distribution checksum is
`6f74b601422d6d6fc4e1f9a1ab6522f642c2fdcbc15ae33ebd30ba3d7198e854`.

## Focused Boot 4 modules

- MVC: `spring-boot-starter-webmvc`
- AOP: `spring-boot-starter-aspectj`
- Flyway: `spring-boot-starter-flyway` plus the PostgreSQL database module
- Jackson 2 compatibility: `spring-boot-jackson2`
- Tests: `spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`,
  `spring-boot-starter-data-jpa-test`, and the BOM-managed JUnit Platform launcher

The former broad web, AOP, and test starters were removed. No leaf dependency versions were
overridden; the Boot 4.1.1 BOM remains authoritative.

## JSON compatibility

Existing public `com.fasterxml.jackson.*` DTO, S2S, report, calibration, and digest contracts remain
on Jackson 2 for this transition slice. MVC explicitly selects Jackson 2 with
`spring.http.converters.preferred-json-mapper=jackson2`. Existing mapper configuration moved from
`spring.jackson` to `spring.jackson2`.

This is the documented compatibility seam and must be replaced by a separately reviewed Jackson 3
contract migration before Boot removes the compatibility module.

## Source migration

Boot 4 moved `EntityScan` to `org.springframework.boot.persistence.autoconfigure.EntityScan`.
Updating that import was the only product-main compile change required after the dependency and
configuration migration. Existing entity scan packages, repositories, security rules, migrations,
and evaluation behavior were preserved.

## Verification

The canonical Gradle wrapper reports Gradle 8.14.5 on Java 21. Product and local composite core
production sources compile with:

```text
./gradlew compileJava --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx768m
BUILD SUCCESSFUL in 39s
```

The first compile correctly exposed the moved `EntityScan` import; the second compile passed after
the import update. Full product tests, MVC Jackson converter proof, runtime boot, OpenAPI generation,
artifact SBOM, and SCA are coordinator gates and are not claimed by this compile checkpoint.

No database, deployment, secret, remote repository, or production state was changed.
