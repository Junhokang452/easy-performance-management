# S4 real runtime / visual findings

## R04 — Table rules cross the following row text (visual FAIL)

- Initial frozen JAR E3582846BF75BEDC95C5AE2855168CB6B344AA11D50560A030C11ADEF3E0FD37.
- Backend 335 tests, actual HTTP 45/45 passed; PDF font/geometry checks passed on 19 unit-evidence pages.
- Main agent rendered and inspected all 19 pages in visual-unit/contact-1..3.png, then full-size ko-landscape-1.png.
- Actual defect: horizontal rule below a row was at `y - rowHeight + 5`; after decrementing y, this placed the rule 5pt above the following baseline and through the following glyphs. Text remained within page geometry, so ordinary extraction/overflow checks did not catch it.
- Requested correction: `+12` gap with current `rowHeight = lines*10+8`, leaving 6pt below the previous row's final baseline and 12pt above the next row. Add regression and regenerate evidence.
- Dedicated runtime stopped before rebuild. Existing PG5432/5433 preserved. Initial HTTP45 pass is historical, not final S4 visual approval.

Resolution: final JAR 5D008F2A56DEC877D56D2BCB1CC43C43395173B3D4978988334156AB65D16DF4; focused14/full336 PASS; final actual HTTP45/45 and all 33 unit/API pages automatic plus visual review PASS. Evidence visual-unit-final/ and visual-api-final/.

The main PDF parser gate now tests horizontal rule segments against every non-space character bounding box. Running this new guard on the initial actual API output failed as expected with `('ko-multipage-200.pdf', 1, 'table rule crosses text')`. Original visual failure PNGs are preserved in `visual-initial/`. This is a regression sensitivity proof, not a new product failure.

## R05 — Browser Accept header rejects binary response

The first real browser download showed the recoverable error alert. Server log confirmed HttpMediaTypeNotAcceptableException: No acceptable representation (16:54:08 local). The shared client defaults to JSON Accept; responseType blob alone does not override it. Matched existing XLSX/SVG exports by adding explicit Accept */* to the PDF request, permitting both PDF success and JSON error. Final browser15/15 PASS, including explicit Accept assertion, actual PDF downloads, unsupported-glyph422 and corrected retry. The browser harness also corrected its picker role from textbox to combobox (test-only).
