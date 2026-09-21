# Framework baseline SBOM/SCA scan

- Input: `/tmp/performance-framework-baseline.jar` (70621297 bytes)
- Input SHA-256: `bf9b0ea9e02151bc2ed0c8807494ff0e3f195e159683ecd3663e803e7097f72f`
- Syft: `1.51.0` (`5a8b71e94f4607973145f02e27e01d50b9f7c7bc41e38d40b39606ad138b43b5`)
- Grype: `0.116.1` (`a8fff88f37a08af6a536e162f37f9902ec94af03df9928ee6295dffe7044dc43`)
- SBOM packages: **100**
- Vulnerability matches: **78** — Critical **8**, High **27**, Medium **30**, Low **13**

## Database evidence

- Schema: `v6.1.9`
- Built: `2026-09-06T06:27:35Z` (data day `2026-09-06`)
- Valid: `true`
- Local DB: `/home/samsung/.cache/grype/db/6/vulnerability.db` (2143162368 bytes)
- Local DB SHA-256: `b46475b87396aac20557f409356989900390786038759b213dc69a057051b598`
- Published archive SHA-256 from DB metadata: `3c93034d0475d25fc82addd47b2300f99564ea4f35fd9e0b957b701049a15020`
- `import.json` SHA-256: `5cf4f996f43ab32d70733fa4d03ec2e650f3c1e8b8566981357fb76ae840fc37`

## Reproduction commands

```bash
TOOLS=/home/samsung/code/_workspace/easy-time-ware-current-head-audit-20260831/40_approved_dev_upgrade/security-tools
OUT=/home/samsung/code/easy-performance-management/_workspace/framework-20260907
$TOOLS/syft scan /tmp/performance-framework-baseline.jar -o syft-json=$OUT/scan-baseline-sbom.json
GRYPE_DB_AUTO_UPDATE=false GRYPE_CHECK_FOR_APP_UPDATE=false $TOOLS/grype sbom:$OUT/scan-baseline-sbom.json -o json > $OUT/scan-baseline-grype.json
GRYPE_DB_AUTO_UPDATE=false GRYPE_CHECK_FOR_APP_UPDATE=false $TOOLS/grype db status -o json
```

No custom Grype configuration or vulnerability suppression was supplied. High findings remain included. The scanner descriptor contains only Grype’s built-in kernel-header ignore patterns, which do not match this Java archive.

## Critical and High findings

| Severity | Package | Installed | CVE | Advisory | Fixed version |
|---|---|---:|---|---|---|
| Critical | `spring-security-core` | `6.4.5` | `CVE-2025-41232` | `GHSA-9pp5-9c7g-4r83` | `6.4.6` |
| Critical | `spring-security-web` | `6.4.5` | `CVE-2026-22732` | `GHSA-mf92-479x-3373` | None published |
| Critical | `tomcat-embed-core` | `10.1.40` | `CVE-2026-43515` | `GHSA-5m62-pw8w-7w9f` | `10.1.55` |
| Critical | `tomcat-embed-core` | `10.1.40` | `CVE-2026-65905` | `GHSA-9xv2-5v5q-p794` | `10.1.58` |
| Critical | `tomcat-embed-core` | `10.1.40` | `CVE-2026-65182` | `GHSA-gcx9-497g-6cp6` | `10.1.58` |
| Critical | `tomcat-embed-core` | `10.1.40` | `CVE-2026-68525` | `GHSA-h3x4-894j-xpx5` | `10.1.58` |
| Critical | `tomcat-embed-core` | `10.1.40` | `CVE-2026-43512` | `GHSA-h6fc-48rj-7qqh` | `10.1.55` |
| Critical | `tomcat-embed-core` | `10.1.40` | `CVE-2026-41293` | `GHSA-r29c-68gh-xp6x` | `10.1.55` |
| High | `jackson-core` | `2.18.3` | Not assigned | `GHSA-r7wm-3cxj-wff9` | `2.18.8` |
| High | `jackson-databind` | `2.18.3` | `CVE-2026-54512` | `GHSA-j3rv-43j4-c7qm` | `2.18.8` |
| High | `jackson-databind` | `2.18.3` | `CVE-2026-54513` | `GHSA-rmj7-2vxq-3g9f` | `2.18.8` |
| High | `micrometer-core` | `1.14.6` | `CVE-2026-40984` | `GHSA-g3pr-3p32-fp23` | None published |
| High | `postgresql` | `42.7.5` | `CVE-2026-42198` | `GHSA-98qh-xjc8-98pq` | `42.7.11` |
| High | `postgresql` | `42.7.5` | `CVE-2025-49146` | `GHSA-hq9p-pm7w-8p54` | `42.7.7` |
| High | `postgresql` | `42.7.5` | `CVE-2026-54291` | `GHSA-j92g-9f8w-j867` | `42.7.12` |
| High | `spring-boot` | `3.4.5` | `CVE-2026-40973` | `GHSA-wwpq-f5c3-7hvx` | None published |
| High | `spring-core` | `6.2.6` | `CVE-2025-41249` | `GHSA-jmp9-x22r-554x` | `6.2.11` |
| High | `spring-data-commons` | `3.4.5` | `CVE-2026-41695` | `GHSA-88fw-v6x4-3f58` | None published |
| High | `spring-data-commons` | `3.4.5` | `CVE-2026-41716` | `GHSA-9fw2-h3hf-293r` | None published |
| High | `spring-expression` | `6.2.6` | `CVE-2026-41850` | `GHSA-r5w3-xv2f-j59q` | `6.2.19` |
| High | `spring-security-core` | `6.4.5` | `CVE-2025-41248` | `GHSA-8v5q-rhf3-jphm` | `6.4.10` |
| High | `spring-webmvc` | `6.2.6` | `CVE-2026-41845` | `GHSA-3chg-m5w7-qfv5` | `6.2.19` |
| High | `spring-webmvc` | `6.2.6` | `CVE-2026-41842` | `GHSA-x23c-287f-qqv5` | `6.2.19` |
| High | `tomcat-embed-core` | `10.1.40` | `CVE-2025-53506` | `GHSA-25xr-qj8w-c4vf` | `10.1.43` |
| High | `tomcat-embed-core` | `10.1.40` | `CVE-2026-24880` | `GHSA-563x-q5rq-57qp` | `10.1.52` |
| High | `tomcat-embed-core` | `10.1.40` | `CVE-2026-43513` | `GHSA-5mp6-jrq3-r938` | `10.1.55` |
| High | `tomcat-embed-core` | `10.1.40` | `CVE-2026-42498` | `GHSA-fv25-8xcx-gqjc` | `10.1.55` |
| High | `tomcat-embed-core` | `10.1.40` | `CVE-2025-48989` | `GHSA-gqp3-2cvr-x8m3` | `10.1.44` |
| High | `tomcat-embed-core` | `10.1.40` | `CVE-2026-41284` | `GHSA-gx5v-xp9w-j4cg` | `10.1.55` |
| High | `tomcat-embed-core` | `10.1.40` | `CVE-2025-48988` | `GHSA-h3gc-qfqq-6h8f` | `10.1.42` |
| High | `tomcat-embed-core` | `10.1.40` | `CVE-2026-24734` | `GHSA-mgp5-rv84-w37q` | `10.1.52` |
| High | `tomcat-embed-core` | `10.1.40` | `CVE-2026-34483` | `GHSA-rv64-5gf8-9qq8` | `10.1.54` |
| High | `tomcat-embed-core` | `10.1.40` | `CVE-2025-55752` | `GHSA-wmwf-9ccg-fff5` | `10.1.45` |
| High | `tomcat-embed-core` | `10.1.40` | `CVE-2025-52520` | `GHSA-wr62-c79q-cv37` | `10.1.43` |
| High | `tomcat-embed-core` | `10.1.40` | `CVE-2026-34487` | `GHSA-x4m4-345f-5h5g` | `10.1.54` |

The machine-readable finding details, including descriptions, fix state, and package locations, are in `scan-baseline-summary.json`. Raw Grype output is preserved in `scan-baseline-grype.json`, and the complete Syft SBOM is in `scan-baseline-sbom.json`.
