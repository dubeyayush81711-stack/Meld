import sys,re,subprocess,xml.etree.ElementTree as ET
def dump():
    subprocess.run(['adb','shell','uiautomator','dump','/sdcard/u.xml'],capture_output=True)
    x=subprocess.run(['adb','exec-out','cat','/sdcard/u.xml'],capture_output=True,text=True).stdout
    return ET.fromstring(x[x.find('<'):])
def nodes(r):
    for n in r.iter('node'):
        m=re.findall(r'\d+',n.get('bounds'))
        if len(m)==4:
            a,b,c,d=map(int,m); yield n,((a+c)//2,(b+d)//2)
mode,pat=sys.argv[1],sys.argv[2]
r=dump()
if mode=='find':   # print center of first node whose text/desc matches
    for n,(x,y) in nodes(r):
        if re.search(pat,n.get('text') or '',re.I) or re.search(pat,n.get('content-desc') or '',re.I):
            print(x,y); sys.exit(0)
    sys.exit(1)
if mode=='edit':   # nth EditText
    es=[c for n,c in nodes(r) if 'EditText' in n.get('class','')]
    i=int(pat)
    if i<len(es): print(*es[i]); sys.exit(0)
    sys.exit(1)
