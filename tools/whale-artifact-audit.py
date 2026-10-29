from pathlib import Path
import json, base64, zipfile, re, xml.etree.ElementTree as ET
from PIL import Image, ImageOps, ImageDraw
root=Path('docs/guides/poc/tl-management/whale')
qa=Path('C:/Users/Public/Documents/ESTsoft/CreatorTemp/whale-manual-20261028')
diagrams=json.loads((root/'sources/diagrams-base64.json').read_text(encoding='utf8'))
for d in diagrams:
    raw=base64.b64decode(d['b64']); (root/'sources'/d['title']).write_bytes(raw)
    tree=ET.fromstring(raw)
    labels=[x.attrib.get('value','') for x in tree.iter('mxCell') if x.attrib.get('value')]
    print(d['title'], ':', ' / '.join(labels)[:1300])
for deck in root.glob('Whale_*.pptx'):
    with zipfile.ZipFile(deck) as z:
        slides=[n for n in z.namelist() if re.fullmatch(r'ppt/slides/slide\d+.xml',n)]
        for s in slides: ET.fromstring(z.read(s))
        text='\n'.join(''.join(ET.fromstring(z.read(s)).itertext()) for s in slides)
        assert '\ufffd' not in text
        assert 'TODO' not in text
    imgs=sorted((qa/(deck.stem.replace('.','-')+'-revised')).glob('*.PNG'),key=lambda x:int(re.search(r'(\d+)\.PNG$',x.name,re.I).group(1)))
    if not imgs: imgs=sorted((qa/deck.stem).glob('*.png'),key=lambda x:int(re.search(r'(\d+)\.png$',x.name,re.I).group(1)))
    assert len(imgs)==len(slides),(len(imgs),len(slides))
    prefix='api' if '_API_' in deck.stem else 'user'
    for offset in range(0,len(imgs),9):
        canvas=Image.new('RGB',(1500,930),'#d8e1e0'); draw=ImageDraw.Draw(canvas)
        for j,f in enumerate(imgs[offset:offset+9]):
            im=Image.open(f);im.thumbnail((490,276));x=(j%3)*500+5;y=(j//3)*310+25
            canvas.paste(im,(x,y));draw.text((x,y-20),f'{prefix} {offset+j+1}',fill='black')
        canvas.save(qa/f'{prefix}-contact-{offset//9+1}.jpg')
    print(deck.name, 'XML + rendered slides:',len(slides))
