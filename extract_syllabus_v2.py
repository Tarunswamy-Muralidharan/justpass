#!/usr/bin/env python3
"""
Extract syllabus data from Anna University R2021 PDF files and produce a JSON file.
Uses PyMuPDF (fitz) for fast text extraction.

V2: Includes ALL subjects from each department's curriculum table (semesters 1-8),
including common/shared subjects (HS, MA, PH, CY, GE, BS, etc.) that were
previously filtered out.
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

# Subject header pattern (for syllabus text extraction)
# Handles both "L T P C" and "LT P C" (some PDFs have no space between L and T)
SUBJECT_HEADER_RE = re.compile(
    r'^[ ]*([A-Z]{2,4}\d{3,4})\s+(.+?)\s+L\s*T\s+P\s+C\s*$',
    re.MULTILINE
)

# Credits line pattern
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
    """Parse semester curriculum tables to get course code -> (semester, title, credits) mapping.

    Returns dict: code -> {'semester': int, 'title': str, 'credits': str}
    """
    code_info = {}
    curriculum_start_page = -1

    # Find where curriculum tables start
    for page_idx, page_text in enumerate(pages[:35]):
        if 'CURRICULUM AND SYLLABI' in page_text:
            curriculum_start_page = page_idx
            break

    if curriculum_start_page < 0:
        return code_info

    for page_idx in range(curriculum_start_page, min(curriculum_start_page + 15, len(pages))):
        page_text = pages[page_idx]

        # Build semester position markers
        sem_positions = []
        for m in re.finditer(r'SEMESTER\s*[–-]?\s*([IVX]+)', page_text):
            roman = m.group(1).strip()
            sem_num = ROMAN_MAP.get(roman, 0)
            if 1 <= sem_num <= 8:
                sem_positions.append((m.start(), sem_num))

        if not sem_positions:
            continue

        # Parse line by line to extract code, title, and credits
        lines = page_text.split('\n')
        line_positions = []
        pos = 0
        for line in lines:
            line_positions.append(pos)
            pos += len(line) + 1  # +1 for \n

        for li, line in enumerate(lines):
            # Look for course code in line
            code_match = re.search(r'\b([A-Z]{2,4}\d{3,4})\b', line)
            if not code_match:
                continue

            code = code_match.group(1)
            prefix = re.match(r'^([A-Z]+)', code).group(1)

            if not (5 <= len(code) <= 7) or prefix in NOISE_PREFIXES:
                continue
            if code in code_info:
                continue

            # Determine semester from position
            line_pos = line_positions[li]
            assigned_sem = 0
            for sem_pos, sem_num in sem_positions:
                if sem_pos < line_pos:
                    assigned_sem = sem_num
                else:
                    break

            if assigned_sem == 0:
                continue

            # Extract title: text after the code on same line and possibly next lines
            after_code = line[code_match.end():].strip()
            # Remove leading number/dot patterns and category codes
            after_code = re.sub(r'^[\s.]+', '', after_code)

            # Build title from text between code and numeric credits
            title_parts = []
            # Check current line for title text
            # The title is between the code and category/credits columns
            # Try to extract from current and next few lines
            search_lines = lines[li:min(li+4, len(lines))]
            search_text = ' '.join(search_lines)

            # Find the code position in combined text
            code_pos_in_search = search_text.find(code)
            if code_pos_in_search >= 0:
                after = search_text[code_pos_in_search + len(code):]
                # Extract title: everything up to category code or credit numbers
                # Categories: HSMC, BSC, ESC, PCC, EEC, MC
                title_match = re.match(
                    r'\s+(.+?)(?:\s+(?:HSMC|BSC|ESC|PCC|EEC|MC|THEORY|PRACTICALS)\b|\s+\d\s+\d\s+\d)',
                    after
                )
                if title_match:
                    title = title_match.group(1).strip()
                else:
                    # Fallback: grab text after code, clean up
                    title = re.sub(r'\s+', ' ', after[:80]).strip()
                    title = re.split(r'\s+\d\s+\d', title)[0].strip()
            else:
                title = ''

            # Clean title
            title = re.sub(r'^\d+\.\s*', '', title)  # Remove leading "1. "
            title = re.sub(r'\s+', ' ', title).strip()
            title = title.rstrip(' -/$#*')
            # Remove trailing category codes
            title = re.sub(r'\s+(HSMC|BSC|ESC|PCC|EEC|MC)\s*$', '', title)

            # Extract credits from nearby lines
            credits_str = ''
            for check_line in lines[li:min(li+3, len(lines))]:
                # Look for L T P C pattern (4 numbers)
                cm = re.search(r'(\d(?:\.\d)?)\s+(\d(?:\.\d)?)\s+(\d(?:\.\d)?)\s+(\d(?:\.\d)?)', check_line)
                if cm:
                    credits_str = f"{cm.group(1)} {cm.group(2)} {cm.group(3)} {cm.group(4)}"
                    break

            if title and len(title) > 2:
                code_info[code] = {
                    'semester': assigned_sem,
                    'title': title,
                    'credits': credits_str,
                }

        # Stop conditions
        if 'TOTAL CREDITS' in page_text:
            # Check if there are more semesters on next pages
            pass
        if len(code_info) > 10:
            if 'PROFESSIONAL ELECTIVE COURSES' in page_text or 'SUMMARY' in page_text:
                break

    return code_info


def parse_subjects(pages):
    """Parse subject syllabi from PDF pages. Returns dict: code -> subject_info."""
    full_text = '\n'.join(pages)

    headers = list(SUBJECT_HEADER_RE.finditer(full_text))

    subjects = {}
    for i, header in enumerate(headers):
        code = header.group(1).strip()
        title = header.group(2).strip()
        title = re.sub(r'\s+', ' ', title)
        title = title.rstrip(' -/')

        start = header.end()

        pre_credits = full_text[start:start + 300]
        credits_match = CREDITS_RE.search(pre_credits)
        credits_str = ''
        if credits_match:
            before_credits = pre_credits[:credits_match.start()].strip()
            if before_credits and not before_credits.startswith('COURSE'):
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

        if code not in subjects:  # Keep first occurrence
            subjects[code] = {
                'code': code,
                'title': title,
                'credits': credits_str,
                'syllabus': syllabus if len(syllabus) > 50 else ''
            }

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


def build_global_syllabus_pool():
    """Build a global pool of all syllabi across all department PDFs.

    This allows cross-referencing: if a common subject's syllabus isn't in
    one dept's PDF, we can find it from another dept's PDF.
    """
    global_pool = {}  # code -> {code, title, credits, syllabus}
    for dept in DEPARTMENTS:
        pdf_path = f'{BASE_DIR}/{dept}_R2021.pdf'
        pages = extract_full_text(pdf_path)
        syllabus_map = parse_subjects(pages)
        for code, subj in syllabus_map.items():
            if code not in global_pool or (not global_pool[code]['syllabus'] and subj['syllabus']):
                global_pool[code] = subj
    return global_pool


def process_department(dept, global_pool):
    """Process a single department's PDF."""
    pdf_path = f'{BASE_DIR}/{dept}_R2021.pdf'
    print(f'Processing {dept}...', flush=True)

    t0 = time.time()
    pages = extract_full_text(pdf_path)
    print(f'  Extracted {len(pages)} pages in {time.time()-t0:.1f}s', flush=True)

    # Get curriculum table: code -> {semester, title, credits}
    curriculum_info = parse_curriculum_tables(pages)
    curriculum_codes = set(curriculum_info.keys())
    print(f'  Found {len(curriculum_codes)} codes in curriculum tables', flush=True)

    # Get all syllabi from this PDF: code -> {code, title, credits, syllabus}
    syllabus_map = parse_subjects(pages)
    print(f'  Found {len(syllabus_map)} total subject syllabi in PDF', flush=True)

    dept_prefixes = get_dept_prefixes(dept)
    dept_subjects = []
    seen_codes = set()

    # PASS 1: Add ALL subjects from curriculum table (including common ones)
    for code, info in curriculum_info.items():
        if code in seen_codes:
            continue

        prefix = re.match(r'^([A-Z]+)', code)
        if not prefix:
            continue

        if is_open_elective_code(code):
            continue

        # Skip IP (Induction Programme) - no real syllabus
        if code.startswith('IP'):
            continue

        seen_codes.add(code)

        # Try to get syllabus from this PDF first
        if code in syllabus_map and syllabus_map[code]['syllabus']:
            subj = syllabus_map[code].copy()
            subj['semester'] = info['semester']
            if len(info['title']) > len(subj['title']):
                subj['title'] = info['title']
            if not subj['credits'] and info['credits']:
                subj['credits'] = info['credits']
            dept_subjects.append(subj)
        elif code in global_pool and global_pool[code]['syllabus']:
            # Fallback: get syllabus from another dept's PDF
            subj = global_pool[code].copy()
            subj['semester'] = info['semester']
            # Prefer curriculum table title
            if info['title'] and len(info['title']) > 2:
                subj['title'] = info['title']
            if not subj['credits'] and info['credits']:
                subj['credits'] = info['credits']
            dept_subjects.append(subj)
        else:
            # No syllabus found anywhere — include with curriculum table info
            dept_subjects.append({
                'code': code,
                'title': info['title'],
                'credits': info['credits'],
                'syllabus': '',
                'semester': info['semester'],
            })

    # PASS 2: Add dept-specific subjects found in syllabus but NOT in curriculum
    # (professional electives, etc.)
    for code, subj in syllabus_map.items():
        if code in seen_codes:
            continue

        prefix = re.match(r'^([A-Z]+)', code)
        if not prefix:
            continue
        prefix_str = prefix.group(1)

        if prefix_str in NOISE_PREFIXES:
            continue
        if is_open_elective_code(code):
            continue

        # Only include if it has a department prefix (electives etc.)
        if prefix_str in dept_prefixes:
            subj_copy = subj.copy()
            subj_copy['semester'] = 0  # Unknown semester for electives
            dept_subjects.append(subj_copy)
            seen_codes.add(code)

    dept_subjects.sort(key=lambda s: (s.get('semester', 0), s['code']))
    print(f'  Total: {len(dept_subjects)} subjects (curriculum + dept electives)', flush=True)

    return dept_subjects


def main():
    t_start = time.time()

    # Load existing JSON to preserve R2025 entries
    existing_data = {}
    if os.path.exists(OUTPUT_PATH):
        with open(OUTPUT_PATH, 'r', encoding='utf-8') as f:
            existing_data = json.load(f)

    result = {}

    # Preserve R2025 entries
    for key, value in existing_data.items():
        if key.endswith('_R2025'):
            result[key] = value

    # Build global syllabus pool for cross-referencing
    print('Building global syllabus pool from all PDFs...', flush=True)
    t_pool = time.time()
    global_pool = build_global_syllabus_pool()
    print(f'Global pool: {len(global_pool)} unique subject syllabi in {time.time()-t_pool:.1f}s\n', flush=True)

    # Process R2021 departments
    for dept in DEPARTMENTS:
        subjects = process_department(dept, global_pool)
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

    # Reorder: R2021 first, then R2025
    ordered = {}
    for dept in DEPARTMENTS:
        ordered[dept] = result[dept]
    for key in sorted(result.keys()):
        if key.endswith('_R2025'):
            ordered[key] = result[key]

    # Write output
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    with open(OUTPUT_PATH, 'w', encoding='utf-8') as f:
        json.dump(ordered, f, indent=2, ensure_ascii=False)

    # Print summary
    print('\n=== SUMMARY ===')
    total = 0
    for dept in DEPARTMENTS:
        count = len(ordered[dept]['subjects'])
        total += count
        sem_dist = {}
        for s in ordered[dept]['subjects']:
            sem = s['semester']
            sem_dist[sem] = sem_dist.get(sem, 0) + 1
        print(f'{dept}: {count} subjects | semesters: {dict(sorted(sem_dist.items()))}')
    print(f'Total R2021: {total} subjects')

    r2025_count = sum(
        len(v['subjects']) if isinstance(v, dict) and 'subjects' in v else (len(v) if isinstance(v, list) else 0)
        for k, v in ordered.items() if k.endswith('_R2025')
    )
    print(f'Total R2025: {r2025_count} subjects (preserved)')

    print(f'Time: {time.time()-t_start:.1f}s')
    print(f'Output: {OUTPUT_PATH}')
    print(f'File size: {os.path.getsize(OUTPUT_PATH) / 1024:.0f} KB')


if __name__ == '__main__':
    main()
