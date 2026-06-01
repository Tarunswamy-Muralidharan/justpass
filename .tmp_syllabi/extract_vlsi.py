import re, json

raw = open('VLSI.raw.txt', encoding='utf-8', errors='replace').read().splitlines()
layout = open('VLSI.txt', encoding='utf-8', errors='replace').read().splitlines()

# ---- target subjects for sems 0-2 (matches existing R2025 dept scope) ----
SEM = {
    0: ['25HS211', '25HS212', '25HS213'],
    1: ['25MA101', '25PH103', '25CY102', '25EC101', '25HS101', '25HS102', '25GE111', '25GE112', '25BS112'],
    2: ['25MA201', '25PH204', '25EC201', '25EC202', '25EC203', '25HS201', '25EC211', '25EEC01'],
}
LANG_LTPC = '0 0 4 2'   # language electives

# ---- 1. LTPC from summary (layout) tables ----
TAIL = re.compile(r'(?P<L>\d+|-)\s+(?P<T>\d+|-)\s+(?P<P>\d+|-)\s+(?P<C>\d+|Grade)\s+(?P<CA>\d+|-)\s+(?P<ESE>\d+|-)\s+(?P<TOT>\d+|-)\s+[A-Z]{2,3}\s*$')
CODE_L = re.compile(r'^\s*\d+\s+(25[0-9A-Z_]{5,7})\b')
ltpc = {}
for ln in layout:
    cm = CODE_L.match(ln)
    if not cm:
        continue
    tm = TAIL.search(ln)
    if not tm:
        continue
    code = cm.group(1)
    c = tm.group('C')
    cc = '0' if c == 'Grade' else c
    ltpc[code] = f"{tm.group('L')} {tm.group('T')} {tm.group('P')} {cc}".replace('-', '0')

# ---- 2. detail sections from raw text ----
HEADER = re.compile(r'^(?:Third ACM\s+)?(25[0-9A-Z]{5})\s+([A-Z][A-Z0-9 &/\-,\.]{4,}?)(?:\s*\(Common to.*)?\s*$')
FOOTER = re.compile(r'^\s*(Third ACM|\d{1,4}|\d{3,5}\s*)\s*$')

# index header positions
headers = []   # (lineidx, code, title)
for i, ln in enumerate(raw):
    m = HEADER.match(ln.rstrip())
    if m:
        title = re.sub(r'\s+', ' ', m.group(2)).strip()
        # reject lines that are clearly not course titles (unit markers etc.)
        if title and not title.startswith(('UNIT', 'LIST OF', 'TOTAL', 'TEXT', 'REFERENCE')):
            headers.append((i, m.group(1), title))

# first proper header occurrence per code
hdr_by_code = {}
for idx, (li, code, title) in enumerate(headers):
    if code not in hdr_by_code:
        hdr_by_code[code] = (idx, li, title)

def clean_body(lines):
    out = []
    for ln in lines:
        s = ln.rstrip()
        if FOOTER.match(s):
            continue
        if re.match(r'^\s*\(Common to.*\)\s*$', s):
            continue
        # strip trailing "Third ACM" / page id appended to a content line
        s = re.sub(r'\s+Third ACM\s*$', '', s)
        s = re.sub(r'\s+\d{3,5}\s*$', '', s)
        out.append(s)
    # collapse 3+ blank lines to 1, trim
    text = '\n'.join(out)
    text = re.sub(r'\n{3,}', '\n\n', text).strip()
    # drop the trailing CO-PO/PSO mapping grid (noisy matrix), keep units +
    # textbooks + course outcomes — matches the existing R2025 data convention
    text = re.split(r'\n\s*(?:COs?\s*[-/]\s*POs?|CO\s*/\s*PO|.*PSOs?\s+MAPPING|.*POs?\s+MAPPING)', text, maxsplit=1)[0].strip()
    return text

def body_for(code):
    if code not in hdr_by_code:
        return None, None
    idx, li, title = hdr_by_code[code]
    nxt = headers[idx + 1][0] if idx + 1 < len(headers) else len(raw)
    return clean_body(raw[li + 1:nxt]), title

# ---- 3. build subjects ----
subjects = []
missing = []
for sem, codes in SEM.items():
    for code in codes:
        body, title = body_for(code)
        if body is None:
            missing.append(code)
            continue
        cred = ltpc.get(code) or (LANG_LTPC if code.startswith('25HS21') else '0 0 0 0')
        subjects.append({
            'code': code,
            'title': title,
            'semester': sem,
            'credits': cred,
            'syllabus': body,
        })

print('built', len(subjects), 'subjects; missing detail:', missing)
for s in subjects:
    print(f"  sem{s['semester']} {s['code']:9} [{s['credits']}] {s['title'][:50]}  (syll {len(s['syllabus'])} chars)")

json.dump({'subjects': subjects}, open('vlsi_r2025.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('wrote vlsi_r2025.json')
