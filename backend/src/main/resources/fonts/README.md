# Bundled PDF font

Unmodified NanumGothic-Regular.ttf from Google Fonts, path `ofl/nanumgothic` at commit `133ccbee9a8b408eb71f31a36ccb9116f5c695ad`.

- Source: https://github.com/google/fonts/tree/133ccbee9a8b408eb71f31a36ccb9116f5c695ad/ofl/nanumgothic
- License: SIL Open Font License 1.1; original copyright and license retained in OFL.txt.
- Font bytes: 2054744; SHA-256 `76f45ef4a6bcff344c837c95a7dcc26e017e38b5846d5ae0cdcb5b86be2e2d31`.
- OFL.txt SHA-256 `eeacf16032901d0ed0456876ec77b8f0fda6b3fecec7d972f8543eb602e6c30f`.
- No system font lookup or request-time network font fetching. Korean and English are the initial PDF label languages; unsupported input glyphs must have an explicit output policy and test.

The application dependency is pinned separately in build.gradle.kts. Official PDFBox release/security references: https://pdfbox.apache.org/download.html and https://pdfbox.apache.org/security.html (checked 2026-09-08). PDF generation does not load user-supplied PDFs, HTML, images, URLs, scripts or fonts.
