# Framework final SBOM/SCA scan

- Final JAR: `/tmp/performance-framework-final.jar` (71234877 bytes)
- Final JAR SHA-256: `1e83919664e3ec9eee5923292d45f7fb0424278413c6f609f1ce0306f3ee3a8b`
- Build JAR copy hash match: **PASS**
- Syft: `1.51.0`; Grype: `0.116.1`
- Final SBOM packages: **121**
- Final vulnerability matches: **0**; ignored matches: **0**

## Severity comparison

| Severity | Baseline | Final | Delta |
|---|---:|---:|---:|
| Critical | 8 | 0 | -8 |
| High | 27 | 0 | -27 |
| Medium | 30 | 0 | -30 |
| Low | 13 | 0 | -13 |
| Negligible | 0 | 0 | +0 |
| Unknown | 0 | 0 | +0 |
| **Total** | **78** | **0** | **-78** |

Critical + High changed from **35** to **0** (-35). This is the result for the fixed local DB snapshot below; it is not a claim about advisories published after that snapshot.

## Fixed database provenance

- Same database metadata as baseline: **TRUE**
- Schema: `v6.1.9`
- Built: `2026-09-06T06:27:35Z` (data day `2026-09-06`)
- Valid: `true`
- Local DB SHA-256: `b46475b87396aac20557f409356989900390786038759b213dc69a057051b598`
- Published archive SHA-256: `3c93034d0475d25fc82addd47b2300f99564ea4f35fd9e0b957b701049a15020`
- `import.json` SHA-256: `5cf4f996f43ab32d70733fa4d03ec2e650f3c1e8b8566981357fb76ae840fc37`

## Suppression evidence

No custom Grype configuration or vulnerability suppression was supplied. `GRYPE_DB_AUTO_UPDATE=false` and `GRYPE_CHECK_FOR_APP_UPDATE=false` were set. Grype reported zero ignored matches. The descriptor contains only its built-in kernel-header ignore patterns, which do not apply to this Java archive.

## Selected dependency versions in the final JAR

| Package | Final version(s) |
|---|---:|
| `jackson-core` | `2.21.5`, `3.1.5` |
| `jackson-databind` | `2.21.5`, `3.1.5` |
| `micrometer-core` | `1.17.1` |
| `postgresql` | `42.7.13` |
| `spring-boot` | `4.1.1` |
| `spring-core` | `7.0.9` |
| `spring-security-core` | `7.1.1` |
| `tomcat-embed-core` | `11.0.25` |

## Reproduction commands

```bash
TOOLS=/home/samsung/code/_workspace/easy-time-ware-current-head-audit-20260831/40_approved_dev_upgrade/security-tools
OUT=/home/samsung/code/easy-performance-management/_workspace/framework-20260907
cp /home/samsung/code/easy-performance-management/backend/build/libs/easy-performance-management-backend-0.1.0.jar /tmp/performance-framework-final.jar
$TOOLS/syft scan /tmp/performance-framework-final.jar -o syft-json=$OUT/scan-final-sbom.json
GRYPE_DB_AUTO_UPDATE=false GRYPE_CHECK_FOR_APP_UPDATE=false $TOOLS/grype sbom:$OUT/scan-final-sbom.json -o json > $OUT/scan-final-grype.json
GRYPE_DB_AUTO_UPDATE=false GRYPE_CHECK_FOR_APP_UPDATE=false $TOOLS/grype db status -o json > $OUT/scan-final-db-status.json
```

Raw Grype output, the full Syft SBOM, and machine-readable comparison data are preserved alongside this report.
