# Validation record

Date: October 4, 2026.

- Local build and 11 tests pass on Oracle GraalVM JDK 21, Java 17 release target, Maven 3.9.9. The initial GitHub build also passed on JDK 17 and 21.
- Checks cover environment substitution, transport restrictions, token-mode conflicts, all 11 profiles, pool configuration, released provider discovery, and concurrent JDBC token-cache access using a locally signed synthetic token.
- All 11 packaged configuration-only checks pass without cloud credentials.
- Live tests used Oracle AI Database 26ai, server version 23.26.3.3.0, through an mTLS wallet. After explicit owner approval, ADMIN enabled OCI_IAM and created a dedicated TOKEN_DEMO mapping to the tested IAM identity with CREATE SESSION only. Existing password-based schemas were not altered.
- Successful live authentication: OCI CLI file tokens with JDBC and UCP; OCI SDK direct connection builder, cached JDBC supplier, and concurrent UCP; OCI resource provider with UCP physical-connection replacement. Each run reported SESSION_USER=TOKEN_DEMO and AUTHENTICATION_METHOD=TOKEN_GLOBAL. Queries only read DUAL; no application data was modified.
- The first forced-replacement run encountered a pool wait failure. Diagnostic mode now explicitly calls ValidConnection.setInvalid() before returning each connection and allows a bounded 60-second replacement wait. The corrected two-round, eight-borrow provider run passed. MaxConnectionReuseCount alone is not used as proof of replacement; server SIDs can also be recycled.
- Refresh across actual token expiration, Entra authentication, OCI workload identities, and interactive/session-token flows remain **unverified**. These short successful OCI API-key runs do not establish those results. Follow the extended live acceptance procedure in troubleshooting.md.

No wallet, database password, private key, cloud account identifier, or live token is included in this repository. Temporary CLI token files used for validation were removed afterward.
