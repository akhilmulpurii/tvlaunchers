# Draws the TV banner (640x360) and icon (192x192). Usage: make-art.py <oxanium.ttf> <out dir>
import sys, random
from PIL import Image, ImageDraw, ImageFont, ImageFilter
F, OUT = sys.argv[1], sys.argv[2]
BG=(4,8,6); G=(80,255,150); TXT=(216,245,228); LBL=(110,150,128)
def ox(sz,w):
    f=ImageFont.truetype(F,sz); f.set_variation_by_axes([w]); return f
def base(W,H,s):
    im=Image.new('RGB',(W*s,H*s),BG); d=ImageDraw.Draw(im,'RGBA')
    random.seed(3)
    for c in range(0,W*s,11*s):           # faint rain
        top=random.randint(-H*s,H*s); n=random.randint(4,14)
        for k in range(n):
            y=top+k*12*s
            if 0<=y<H*s: d.rectangle([c+3*s,y,c+7*s,y+7*s],fill=(40,140,80,int(90*(1-k/n))))
    return im,d
def hexp(x0,y0,x1,y1,c):
    m=(y0+y1)/2; return [(x0+c,y0),(x1-c,y0),(x1,m),(x1-c,y1),(x0+c,y1),(x0,m)]
def banner(W=640,H=360,s=3):
    im,d=base(W,H,s)
    p=hexp(70*s,110*s,570*s,250*s,40*s)
    d.polygon(p,fill=(4,12,8,235),outline=G+(220,),width=2*s)
    d.line([(116*s,116*s),(524*s,116*s)],fill=(230,255,240,60),width=s)
    f=ox(52*s,300); t='MAXIMA FUTURA'; w=d.textlength(t,font=f); d.text(((W*s-w)/2,140*s),t,font=f,fill=TXT)
    f2=ox(14*s,500); t2='H U D   L A U N C H E R'; w=d.textlength(t2,font=f2); d.text(((W*s-w)/2,205*s),t2,font=f2,fill=G)
    return im.resize((W,H),Image.LANCZOS)
def icon(N=192,s=3):
    im,d=base(N,N,s)
    p=hexp(20*s,50*s,172*s,142*s,26*s)
    d.polygon(p,fill=(4,12,8,235),outline=G+(230,),width=3*s)
    f=ox(52*s,400); t='MF'; w=d.textlength(t,font=f); d.text(((N*s-w)/2,66*s),t,font=f,fill=G)
    return im.resize((N,N),Image.LANCZOS)
banner().save(OUT+'/banner.png'); icon().save(OUT+'/ic_launcher.png')
