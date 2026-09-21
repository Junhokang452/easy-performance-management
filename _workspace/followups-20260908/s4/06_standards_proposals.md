# S4 standards proposals (not applied to shared SoT)

1. Bounded PDF generation: request allowlists, pre-load population cap, coherent snapshot transaction, no-transaction rendering, write-time byte cap, page cap, cooperative deadline, no URL/HTML/image/user-font interpretation.
2. Embedded-font glyph checks: PDFBox `hasGlyph(int)` accepts encoded character/CID semantics; do not pass Unicode code points as if it were a Unicode font coverage test. Validate through encoding/string width and map unsupported glyph failure without exposing source content.
3. Export source hash: encode fields unambiguously (length-prefix or canonical JSON), including null distinction. Plain ':'/'|' concatenation of untrusted text permits identical preimages from distinct field tuples. Hashing does not fix ambiguous serialization.
4. Source provenance: pin dependency version plus font source commit/hash and retain OFL; do not use system fonts for server Korean output.
5. QA: successful build/download is insufficient. Parse actual output, render every page, check last rows, repeated headers, long title/cell, omitted-column privacy and recovery from binary-request JSON errors.

Official technical references checked 2026-09-08: [PDFBox release](https://pdfbox.apache.org/download.html), [security](https://pdfbox.apache.org/security.html), [PDFBox PDCIDFontType2 source](https://github.com/apache/pdfbox/blob/3.0.8/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/font/PDCIDFontType2.java), [font source](https://github.com/google/fonts/tree/133ccbee9a8b408eb71f31a36ccb9116f5c695ad/ofl/nanumgothic).

These are PA-local proposals. No easy-standards/lib/HCM, AGENTS, external service or deployment mutation is authorized/performed by this S4 work.

## 2026-09-08 evidence status

The proposals remain local and are not a shared-SoT change. The S4 implementation evidence is
currently PASS within the bounded scope: backend focused `14/14` and full `336/336` were reported
with bootJar/JAR parity; dedicated PDF API checks are `45/45`; PDF browser checks are `15/15` after
the `Accept: */*` R05 fix; and S1/S2/S3 browser regressions are `13/13`, `16/16`, and `16/16`.
These results use dedicated loopback runtime and synthetic fixtures where stated; they do not claim
external workflow or production-data approval.
