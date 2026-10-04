# Validation record

Date: October 4, 2026.

- Local build and tests: Oracle GraalVM JDK 21, Java 17 release target, Maven 3.9.9. See CI for independent JDK 17/21 runs.
- Checks cover environment substitution, transport restrictions, token-mode conflicts, all 11 profiles, pool configuration, released provider discovery, and concurrent JDBC token-cache access using a locally signed synthetic token.
- Packaged CLI help and configuration-only checks run without cloud credentials.
- A separate read-only preflight connected through a supplied mTLS wallet to Oracle AI Database 26ai, server version 23.26.3.3.0, using database-password authentication. The supplied account has CREATE SESSION; querying the external-provider parameter was not permitted. No schema or identity configuration was changed.
- Live OCI/Entra token login and refresh across expiration have **not** been validated. They require a configured cloud identity, matching database mapping, and the appropriate privileges. The password preflight is not evidence of token authentication. Follow the live acceptance procedure in troubleshooting.md.

No wallet, database password, private key, cloud account identifier, or live token is included in this repository.
