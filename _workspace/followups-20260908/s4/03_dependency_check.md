# New PDF dependency check

2026-09-08. Added dependency `org.apache.pdfbox:pdfbox:3.0.8` is pinned in Gradle. Official download page and security advisory page were read, not just search snippets. PDFBox is Apache-2.0; bundled NanumGothic font is OFL-1.1 with original notice retained (resources/fonts/README.md).

OSV public API `https://api.osv.dev/v1/querybatch` returned HTTP200 for exact-version queries:

| Maven package | Version | OSV result |
|---|---|---|
| org.apache.pdfbox:pdfbox | 3.0.8 | no advisory match |
| org.apache.pdfbox:pdfbox-io | 3.0.8 | no advisory match |
| org.apache.pdfbox:fontbox | 3.0.8 | no advisory match |

Actual response: `{"results":[{},{},{}]}`. This is a point-in-time check of the three added PDFBox modules, not a guarantee of no vulnerabilities, a whole-application SCA, signed SBOM, or deployment approval. No source code, credentials, evaluation or tenant data was sent; queries contained public Maven coordinates only.

The renderer uses generated text and the fixed bundled font only; it does not parse external/user PDFs or invoke PDFBox example extraction code.

Sources: [Apache PDFBox release](https://pdfbox.apache.org/download.html), [Apache security advisory](https://pdfbox.apache.org/security.html), [OSV API](https://google.github.io/osv.dev/api/), [font provenance](https://github.com/google/fonts/tree/133ccbee9a8b408eb71f31a36ccb9116f5c695ad/ofl/nanumgothic).
