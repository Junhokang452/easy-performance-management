#!/usr/bin/env python3
"""Render all final S4 sample pages with Poppler and verify text/font geometry.

PNG/contact sheets are visual QA intermediates, not delivered report contents.
"""
import argparse
import json
from pathlib import Path
import subprocess
import pdfplumber
from pypdf import PdfReader
from PIL import Image, ImageDraw


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--pdf-dir', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--pdftoppm', default='C:/Users/SAMSUNG/.cache/codex-runtimes/codex-primary-runtime/dependencies/native/poppler/Library/bin/pdftoppm.exe')
    args = parser.parse_args()
    pdfs = sorted(Path(args.pdf_dir).glob('*.pdf'))
    if not pdfs:
        raise SystemExit('No PDFs to validate')
    output = Path(args.output); output.mkdir(parents=True, exist_ok=True)
    results, images = [], []
    for file in pdfs:
        reader = PdfReader(file)
        font_count = 0
        for page in reader.pages:
            for font in page['/Resources']['/Font'].values():
                font = font.get_object()
                descriptor = font['/DescendantFonts'][0].get_object()['/FontDescriptor'] if '/DescendantFonts' in font else font['/FontDescriptor']
                assert '/FontFile2' in descriptor or '/FontFile3' in descriptor, 'Every font must be embedded'
                font_count += 1
        with pdfplumber.open(file) as pdf:
            for index, page in enumerate(pdf.pages):
                for char in page.chars:
                    assert char['x0'] >= -0.5 and char['x1'] <= page.width + 0.5, (file.name, index + 1, 'horizontal overflow')
                    assert char['top'] >= -0.5 and char['bottom'] <= page.height + 0.5, (file.name, index + 1, 'vertical overflow')
                for edge in page.edges:
                    if edge.get('orientation') != 'h' or edge['x1'] - edge['x0'] < 100:
                        continue
                    for char in page.chars:
                        assert not (char['text'].strip() and char['x0'] < edge['x1'] and char['x1'] > edge['x0'] and
                                    char['top'] < edge['top'] < char['bottom']), (file.name, index + 1, 'table rule crosses text')
                text = page.extract_text() or ''
                assert 'UTC' in text and 'Source:' in text and ('기밀' in text or 'Confidential' in text), (file.name, index + 1, 'page provenance missing')
        prefix = output / file.stem
        subprocess.run([args.pdftoppm, '-r', '120', '-png', str(file.resolve()), str(prefix.resolve())], check=True, timeout=60)
        pages = sorted(output.glob(file.stem + '-*.png'), key=lambda item: int(item.stem.rsplit('-', 1)[1]))
        assert len(pages) == len(reader.pages)
        images.extend(pages)
        results.append({'pdf': str(file.resolve()), 'pages': len(pages), 'embeddedFontUses': font_count,
                        'geometryPassed': True, 'pngs': [str(p.resolve()) for p in pages]})
    # Show every page in bounded contact sheets, while preserving full-size PNGs.
    sheets = []
    for start in range(0, len(images), 9):
        batch = images[start:start + 9]
        sheet = Image.new('RGB', (1500, ((len(batch) + 2) // 3) * 400), 'white')
        draw = ImageDraw.Draw(sheet)
        for index, file in enumerate(batch):
            with Image.open(file) as page:
                page.thumbnail((480, 370))
                x = (index % 3) * 500 + (500 - page.width) // 2
                y = (index // 3) * 400 + 22
                sheet.paste(page, (x, y))
                draw.text(((index % 3) * 500 + 10, (index // 3) * 400 + 5), file.name, fill='black')
        target = output / f'contact-{start // 9 + 1}.png'; sheet.save(target); sheets.append(str(target.resolve()))
    result = {'completed': True, 'pdfs': results, 'totalPages': len(images), 'contactSheets': sheets,
              'visualReview': 'PENDING: main agent must inspect contact sheets and full-size pages'}
    (output / 'result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'pdfs': len(results), 'pages': len(images), 'contactSheets': sheets}))


if __name__ == '__main__':
    main()
