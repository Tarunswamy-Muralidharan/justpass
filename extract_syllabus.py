#!/usr/bin/env python3
"""
Extract syllabus data from Anna University R2021 PDF files and produce a JSON file.
Uses PyMuPDF (fitz) for fast text extraction.
"""

import fitz  # PyMuPDF
import re
import json
import sys
import os
import time

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

BASE_DIR = 'C:/Users/tmswa/Desktop/Anna_University_R2021_Syllabus'
OUTPUT_PATH = 'C:/Users/tmswa/AndroidStudioProjects/AttendanceWidgetLaudea/app/src/main/assets/syllabus_r2021.json'

DEPARTMENTS = ['AIDS', 'CIVIL', 'CSBS', 'CSE', 'ECE', 'EEE', 'MECH']

# Common course code prefixes shared across ALL departments (skip these)
COMMON_PREFIXES = {
    'HS', 'MA', 'PH', 'CY', 'GE', 'BS', 'IP', 'MX', 'BE',
}

# Subject header pattern
SUBJECT_HEADER_RE = re.compile(
    r'^[ ]*([A-Z]{2,4}\d{3,4})\s+(.+?)\s+L\s+T\s+P\s+C\s*$',
    re.MULTILINE
)

# Credits line pattern - handles both integer (3 0 0 3) and decimal (0 0 3 1.5) credits
# Also handles cases where title continuation is on the same line as credits
CREDITS_RE = re.compile(r'(\d(?:\.\d)?)\s+(\d(?:\.\d)?)\s+(\d(?:\.\d)?)\s+(\d(?:\.\d)?)\s*$', re.MULTILINE)

# CO-PO mapping pattern (end of syllabus section)
COPO_RE = re.compile(r"CO.?s?.?\s*[-–]?\s*PO.?s?.*MAPPING|CO\s+PO\s*1|CO1\s+\d", re.IGNORECASE)

ROMAN_MAP = {'I': 1, 'II': 2, 'III': 3, 'IV': 4, 'V': 5, 'VI': 6, 'VII': 7, 'VIII': 8}
NOISE_PREFIXES = {'PO', 'CO', 'PSO', 'PEO', 'TOTAL', 'NCC', 'AVG', 'SL', 'NO', 'MC'}


def extract_full_text(pdf_path):
    """Extract all text from a PDF file using PyMuPDF."""
    doc = fitz.open(pdf_path)
    pages = []
    for page in doc:
        pages.append(page.get_text())
    doc.close()
    return pages


def parse_curriculum_tables(pages):
    """Parse semester curriculum tables to get course code -> semester mapping.

    Handles multiple semesters per page by tracking positions of semester headers
    and assigning each course code to the nearest preceding semester header.
    """
    code_to_semester = {}
    found_curriculum_start = False
    curriculum_start_page = -1

    # First pass: find where curriculum tables start
    for page_idx, page_text in enumerate(pages[:35]):
        if 'CURRICULUM AND SYLLABI' in page_text:
            curriculum_start_page = page_idx
            break

    if curriculum_start_page < 0:
        return code_to_semester

    # Second pass: parse from curriculum start
    for page_idx in range(curriculum_start_page, min(curriculum_start_page + 15, len(pages))):
        page_text = pages[page_idx]

        # Build a list of (position, semester_number) for this page
        sem_positions = []
        for m in re.finditer(r'SEMESTER\s*[–-]?\s*([IVX]+)', page_text):
            roman = m.group(1).strip()
            sem_num = ROMAN_MAP.get(roman, 0)
            if 1 <= sem_num <= 8:
                sem_positions.append((m.start(), sem_num))

        if not sem_positions:
            continue

        # Find all course codes on this page with their positions
        for match in re.finditer(r'\b([A-Z]{2,4})(\d{3,4})\b', page_text):
            code = match.group(1) + match.group(2)
            prefix = match.group(1)
            pos = match.start()

            if not (5 <= len(code) <= 7) or prefix in NOISE_PREFIXES:
                continue
            if code in code_to_semester:
                continue

            # Find which semester this code belongs to:
            # the latest semester header that appears BEFORE this code's position
            assigned_sem = 0
            for sem_pos, sem_num in sem_positions:
                if sem_pos < pos:
                    assigned_sem = sem_num
                else:
                    break

            if assigned_sem > 0:
                code_to_semester[code] = assigned_sem

        # Stop conditions
        if 'TOTAL CREDITS' in page_text:
            break
        if len(code_to_semester) > 10:
            if 'PROFESSIONAL ELECTIVE COURSES' in page_text or 'SUMMARY' in page_text:
                break

    return code_to_semester


def parse_subjects(pages):
    """Parse subject syllabi from PDF pages."""
    full_text = '\n'.join(pages)

    headers = list(SUBJECT_HEADER_RE.finditer(full_text))

    subjects = []
    for i, header in enumerate(headers):
        code = header.group(1).strip()
        title = header.group(2).strip()
        title = re.sub(r'\s+', ' ', title)
        # Remove trailing dashes or slashes from titles
        title = title.rstrip(' -/')

        start = header.end()

        # Handle multi-line titles: if the text between header end and credits line
        # contains uppercase words before the credits, append them to title
        pre_credits = full_text[start:start + 300]
        credits_match = CREDITS_RE.search(pre_credits)
        credits_str = ''
        if credits_match:
            # Check for title continuation before credits line
            before_credits = pre_credits[:credits_match.start()].strip()
            if before_credits and not before_credits.startswith('COURSE'):
                # Lines before credits that are all-caps could be title continuation
                title_extra = re.sub(r'\s+', ' ', before_credits).strip()
                if title_extra and title_extra.isupper() and len(title_extra) < 100:
                    title = title + ' ' + title_extra
                    title = title.rstrip(' -/')
            credits_str = f"{credits_match.group(1)} {credits_match.group(2)} {credits_match.group(3)} {credits_match.group(4)}"
            start = start + credits_match.end()

        # End at next subject header
        if i + 1 < len(headers):
            end = headers[i + 1].start()
        else:
            end = len(full_text)

        raw_content = full_text[start:end]

        # Trim at CO-PO mapping table
        copo_match = COPO_RE.search(raw_content)
        if copo_match:
            raw_content = raw_content[:copo_match.start()]

        # Trim at TEXT BOOKS or REFERENCES section
        textbook_match = re.search(r'\n\s*(?:TEXT\s*BOOKS?|REFERENCES?)\s*:', raw_content)
        if textbook_match:
            raw_content = raw_content[:textbook_match.start()]

        syllabus = clean_syllabus_text(raw_content)

        if syllabus and len(syllabus) > 50:
            subjects.append({
                'code': code,
                'title': title,
                'credits': credits_str,
                'syllabus': syllabus
            })

    return subjects


def clean_syllabus_text(text):
    """Clean up syllabus text."""
    text = re.sub(r'^\s*\d{1,3}\s*$', '', text, flags=re.MULTILINE)

    for ch in ['\uf0b7', '\uf0fc', '\uf076', '\uf0d8', '\u2022']:
        text = text.replace(ch, '-')

    lines = text.split('\n')
    cleaned_lines = []
    for line in lines:
        line = line.strip()
        if line:
            line = re.sub(r'\s+', ' ', line)
            cleaned_lines.append(line)

    text = '\n'.join(cleaned_lines)
    text = re.sub(r'\n{3,}', '\n\n', text)

    return text.strip()


def is_open_elective_code(code):
    """Check if a course code is an open elective."""
    prefix = re.match(r'^([A-Z]+)', code)
    if not prefix:
        return False
    p = prefix.group(1)
    return p.startswith('O') and len(p) >= 3


def get_dept_prefixes(dept):
    """Get course code prefixes that belong to this department."""
    prefix_map = {
        'AIDS': {'AD', 'AL', 'CCS', 'CW', 'CS', 'CB'},
        'CIVIL': {'CE', 'CN', 'CCE', 'AG'},
        'CSBS': {'CB', 'CW', 'CS', 'CCS'},
        'CSE': {'CS', 'CCS', 'CB', 'CCW', 'CW'},
        'ECE': {'EC', 'CEC', 'CS'},
        'EEE': {'EE', 'CEE', 'EI', 'EC'},
        'MECH': {'ME', 'CME', 'MF', 'PR', 'CE'},
    }
    return prefix_map.get(dept, set())


def process_department(dept):
    """Process a single department's PDF."""
    pdf_path = f'{BASE_DIR}/{dept}_R2021.pdf'
    print(f'Processing {dept}...', flush=True)

    t0 = time.time()
    pages = extract_full_text(pdf_path)
    print(f'  Extracted {len(pages)} pages in {time.time()-t0:.1f}s', flush=True)

    code_to_semester = parse_curriculum_tables(pages)
    print(f'  Found {len(code_to_semester)} codes in curriculum tables', flush=True)

    dept_prefixes = get_dept_prefixes(dept)

    all_subjects = parse_subjects(pages)
    print(f'  Found {len(all_subjects)} total subject syllabi', flush=True)

    dept_subjects = []
    seen_codes = set()

    for subj in all_subjects:
        code = subj['code']
        if code in seen_codes:
            continue

        prefix = re.match(r'^([A-Z]+)', code)
        if not prefix:
            continue
        prefix_str = prefix.group(1)

        if prefix_str in COMMON_PREFIXES:
            continue
        if is_open_elective_code(code):
            continue

        in_curriculum = code in code_to_semester
        has_dept_prefix = prefix_str in dept_prefixes

        if in_curriculum or has_dept_prefix:
            semester = code_to_semester.get(code, 0)
            subj['semester'] = semester
            dept_subjects.append(subj)
            seen_codes.add(code)

    dept_subjects.sort(key=lambda s: (s.get('semester', 0), s['code']))
    print(f'  Filtered to {len(dept_subjects)} department-specific subjects', flush=True)

    return dept_subjects


def main():
    t_start = time.time()
    result = {}

    for dept in DEPARTMENTS:
        subjects = process_department(dept)
        result[dept] = {
            'subjects': [
                {
                    'code': s['code'],
                    'title': s['title'],
                    'semester': s.get('semester', 0),
                    'credits': s['credits'],
                    'syllabus': s['syllabus']
                }
                for s in subjects
            ]
        }

    # Write output
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    with open(OUTPUT_PATH, 'w', encoding='utf-8') as f:
        json.dump(result, f, indent=2, ensure_ascii=False)

    # Print summary
    print('\n=== SUMMARY ===')
    total = 0
    for dept in DEPARTMENTS:
        count = len(result[dept]['subjects'])
        total += count
        sem_dist = {}
        for s in result[dept]['subjects']:
            sem = s['semester']
            sem_dist[sem] = sem_dist.get(sem, 0) + 1
        print(f'{dept}: {count} subjects | semesters: {dict(sorted(sem_dist.items()))}')
    print(f'Total: {total} subjects')
    print(f'Time: {time.time()-t_start:.1f}s')
    print(f'Output: {OUTPUT_PATH}')
    print(f'File size: {os.path.getsize(OUTPUT_PATH) / 1024:.0f} KB')


if __name__ == '__main__':
    main()
