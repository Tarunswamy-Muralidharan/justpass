import re, json, sys

ROMAN = {'I':1,'II':2,'III':3,'IV':4,'V':5,'VI':6,'VII':7,'VIII':8}
# tail: L T P C CA ESE Total CAT  (C may be 'Grade'; cols may be '-')
TAIL = re.compile(r'(?P<L>\d+|-)\s+(?P<T>\d+|-)\s+(?P<P>\d+|-)\s+(?P<C>\d+|Grade)\s+(?P<CA>\d+|-)\s+(?P<ESE>\d+|-)\s+(?P<TOT>\d+|-)\s+(?P<CAT>[A-Z]{2,3})\s*$')
CODE = re.compile(r'^\s*\d+\s+(?P<code>25[0-9A-Z_]{5,7}(?:/\s*25[0-9A-Z_]{5,7})?)\s+(?P<rest>.*\S)\s*$')

def parse(path, stop_sem=99):
    sem = 0
    out = []  # (sem, code, title, credits, cat)
    lines = open(path, encoding='utf-8', errors='replace').read().splitlines()
    i = 0
    while i < len(lines):
        ln = lines[i].rstrip()
        m = re.match(r'\s*SEMESTER\s+([IVX]+)\s*$', ln)
        if m:
            sem = ROMAN.get(m.group(1), 0)
            if sem > stop_sem: break
            i += 1; continue
        # Stop at credit-distribution summary
        if 'Summary of Credit Distribution' in ln:
            break
        cm = CODE.match(ln)
        if cm and sem:
            rest = cm.group('rest')
            tm = TAIL.search(rest)
            if tm:
                title = rest[:tm.start()].strip()
                # grab continuation title from following lines (indented, no code, no tail)
                j = i+1
                while j < len(lines):
                    nxt = lines[j].rstrip()
                    if not nxt.strip(): break
                    if CODE.match(nxt): break
                    if re.match(r'\s*SEMESTER\s+[IVX]+', nxt): break
                    if re.match(r'\s*(THEORY|PRACTICALS?|MANDATORY|Total|CAT|PC|Course|S\.|\*)', nxt): break
                    if TAIL.search(nxt): break
                    # continuation fragment
                    title = (title + ' ' + nxt.strip()).strip()
                    j += 1
                cred = tm.group('C')
                credits = 0.0 if cred == 'Grade' else float(cred)
                code = re.sub(r'\s+','',cm.group('code'))
                title = re.sub(r'\s+',' ', title).strip(' /')
                out.append((sem, code, title, credits, tm.group('CAT')))
        i += 1
    return out

for f in ['ICE.txt','VLSI.txt']:
    print(f'\n######## {f} ########')
    rows = parse(f)
    cur = None
    for sem,code,title,cr,cat in rows:
        if sem!=cur:
            print(f'--- SEM {sem} ---'); cur=sem
        print(f'  {code:12} cr={cr:<4} [{cat}] {title}')
