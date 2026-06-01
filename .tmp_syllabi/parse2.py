import re, json

ROMAN = {'I':1,'II':2,'III':3,'IV':4,'V':5,'VI':6,'VII':7,'VIII':8}
TAIL = re.compile(r'(?P<L>\d+|-)\s+(?P<T>\d+|-)\s+(?P<P>\d+|-)\s+(?P<C>\d+|Grade)\s+(?P<CA>\d+|-)\s+(?P<ESE>\d+|-)\s+(?P<TOT>\d+|-)\s+(?P<CAT>[A-Z]{2,3})\s*$')
CODE = re.compile(r'^\s*\d+\s+(?P<code>25[0-9A-Z_]{5,7}(?:/\s*25[0-9A-Z_]{5,7})?)\s*(?P<rest>.*)$')
SKIP = re.compile(r'^\s*(THEORY|PRACTICALS?|MANDATORY|Total|CAT\b|PC\b|Course\b|S\.|\*|\d+\s+25)')

def is_cont(nxt):
    if not nxt.strip(): return False
    if CODE.match(nxt): return False
    if re.match(r'\s*SEMESTER\s+[IVX]+', nxt): return False
    if SKIP.match(nxt): return False
    if TAIL.search(nxt): return False
    return True

def parse(path):
    sem=0; out=[]
    lines=open(path,encoding='utf-8',errors='replace').read().splitlines()
    i=0
    while i<len(lines):
        ln=lines[i].rstrip()
        m=re.match(r'\s*SEMESTER\s+([IVX]+)\s*$',ln)
        if m: sem=ROMAN.get(m.group(1),0); i+=1; continue
        if 'Summary of Credit Distribution' in ln: break
        cm=CODE.match(ln)
        if cm and sem:
            rest=cm.group('rest')
            tm=TAIL.search(rest)
            title_acc=''
            if tm:
                title_acc=rest[:tm.start()].strip()
            else:
                # numbers may be on following line(s); accumulate title until we hit a tail
                title_acc=rest.strip()
                j=i+1
                while j<len(lines):
                    nxt=lines[j].rstrip()
                    t2=TAIL.search(nxt)
                    if t2:
                        title_acc=(title_acc+' '+nxt[:t2.start()].strip()).strip()
                        tm=t2; i=j; break
                    if is_cont(nxt):
                        title_acc=(title_acc+' '+nxt.strip()).strip(); j+=1
                    else: break
            if not tm:
                i+=1; continue
            # capture continuation title lines after the tail line
            j=i+1
            while j<len(lines) and is_cont(lines[j].rstrip()):
                title_acc=(title_acc+' '+lines[j].strip()).strip(); j+=1
            cred=tm.group('C'); credits=0.0 if cred=='Grade' else float(cred)
            code=re.sub(r'\s+','',cm.group('code'))
            title=re.sub(r'\s+',' ',title_acc).strip(' /')
            cat=tm.group('CAT')
            out.append({'sem':sem,'code':code,'title':title,'credits':credits,'cat':cat})
        i+=1
    # known title fixes for tamil rows
    for r in out:
        if r['code']=='25HS201' and not r['title']:
            r['title']='Tamils and Technology'
    return out

res={}
for key,f in [('ICE','ICE.txt'),('VLSI','VLSI.txt')]:
    rows=parse(f)
    # keep credits>0 (drops Induction/Activity Point/Mandatory/0-cr Workplace)
    rows=[r for r in rows if r['credits']>0]
    res[key]=rows
    print(f'\n#### {key}: {len(rows)} graded courses ####')
    cur=None
    for r in rows:
        if r['sem']!=cur: print(f'-- SEM {r["sem"]} --'); cur=r['sem']
        el='(E)' if ('Elective' in r['title']) else ''
        print(f'  {r["code"]:11} {r["credits"]:<4} [{r["cat"]}] {r["title"]} {el}')
json.dump(res,open('curriculum.json','w',encoding='utf-8'),indent=1,ensure_ascii=False)
print('\nwrote curriculum.json')
