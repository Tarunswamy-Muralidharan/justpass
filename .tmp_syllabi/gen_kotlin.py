import re, json
data = json.load(open('curriculum.json', encoding='utf-8'))

def clean_title(t):
    t = re.sub(r'\b25[0-9A-Z_]{5,7}\b', '', t)   # strip leaked codes
    t = re.sub(r'\s+', ' ', t).strip(' /')
    return t

def esc(s):
    return s.replace('\\', '\\\\').replace('"', '\\"')

def fmt_credit(c):
    return str(int(c)) + '.0'

def gen(branch, varprefix):
    rows = data[branch]
    bysem = {}
    for r in rows:
        bysem.setdefault(r['sem'], []).append(r)
    out = []
    for sem in sorted(bysem):
        lines = [f'private val r2025{varprefix}Sem{sem} = listOf(']
        for r in bysem[sem]:
            title = clean_title(r['title'])
            cr = fmt_credit(r['credits'])
            if 'Elective' in title:
                lines.append(f'    elective("{esc(title)}", {cr}),')
            else:
                lines.append(f'    sub("{r["code"]}", "{esc(title)}", {cr}),')
        lines[-1] = lines[-1][:-1]   # drop trailing comma
        lines.append(')')
        out.append('\n'.join(lines))
    return '\n'.join(out)

with open('generated_curriculum.kt', 'w', encoding='utf-8') as f:
    f.write('// ---- ICE R2025 ----\n')
    f.write(gen('ICE', 'Ice') + '\n\n')
    f.write('// ---- VLSI (EE-VLSI) R2025 ----\n')
    f.write(gen('VLSI', 'Vlsi') + '\n')
print('done')
