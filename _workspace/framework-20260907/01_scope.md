# Framework migration scope and standards

Authorized suite coordinator continuation received after functional/i18n completion. The selected target and common-core full hermetic result were supplied by the TIME/WARE coordinator task 01a057f1-9ad8-7962-a7d8-38b1c67ac6d9.

Target: Boot4.1.1 / Java21 / Gradle8.14.5 / springdoc3.1.1 / local core1.0.0-SNAPSHOT. Preserve public Jackson2 and existing JSON/S2S/signature behavior. Source decision: /home/samsung/code/_workspace/suite-security-baseline-20260907/02_target_version_decision.md. This explicitly supersedes old Boot3.4.5 skill defaults. Relevant suite standards remain shared-code ADR-007, stack ADR-014, Neon ADR-013, error/JWT/tenancy and API boundary standards.

Ownership: root applies minimal framework port to product-local lib (7c814bc), performs integrated build/runtime/API/SBOM/SCA checks. backend_flow owns backend build/wrapper/main sources. auth_boundary_review owns backend tests and MVC JSON characterization. Existing feature changes and 5locale UI must be preserved. Canonical core and other products are read-only in this task.

Baseline: BE214/214; API97/97; FEbuild/type; i18n7/7 and browser15/15; PostgreSQL synthetic local demo schema20260907.003. Preserve evidence under _workspace/roothr-20260907; put new framework evidence here. No product DB schema changes, external DB access/provisioning, cloud deployment, secrets, or push.

Official migration references: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide and https://docs.spring.io/spring-boot/system-requirements.html. Boot4 requires focused main/test starters and moved auto-config imports; compilation alone is insufficient for converter/auth/wire compatibility.
