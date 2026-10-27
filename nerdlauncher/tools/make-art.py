import sys
from PIL import Image, ImageDraw, ImageFont
F=sys.argv[1]; out=sys.argv[2]
BG=(20,22,27); LINE=(42,46,54); TXT=(230,231,234); DIM=(125,130,140); YEL=(245,183,47)
def sp(d,xy,t,font,fill,track):
    x,y=xy
    for c in t: d.text((x,y),c,font=font,fill=fill); x+=d.textlength(c,font=font)+track
    return x
def banner(W=640,H=360,s=3):
    W2,H2=W*s,H*s; im=Image.new('RGB',(W2,H2),BG); d=ImageDraw.Draw(im)
    m=int(44*s)
    sp(d,(m,int(40*s)),'NERD_OS v0.1',ImageFont.truetype(F,int(20*s)),DIM,int(3*s))
    d.line([(m,int(84*s)),(W2-m,int(84*s))],fill=LINE,width=2*s)
    f=ImageFont.truetype(F,int(118*s))
    x=sp(d,(m-int(6*s),int(118*s)),'>_',f,TXT,0)
    x=sp(d,(x+int(18*s),int(118*s)),'NERD',f,TXT,int(4*s))
    # block cursor
    d.rectangle([x+int(10*s),int(140*s),x+int(40*s),int(238*s)],fill=YEL)
    d.line([(m,int(278*s)),(W2-m,int(278*s))],fill=LINE,width=2*s)
    sp(d,(m,int(296*s)),'LAUNCHER',ImageFont.truetype(F,int(20*s)),DIM,int(5*s))
    return im.resize((W,H),Image.LANCZOS)
def icon(N=192,s=3):
    N2=N*s; im=Image.new('RGB',(N2,N2),BG); d=ImageDraw.Draw(im)
    f=ImageFont.truetype(F,int(96*s))
    w=d.textlength('>_',font=f); x=(N2-w-int(30*s))/2; y=int(40*s)
    sp(d,(x,y),'>_',f,TXT,0)
    d.rectangle([x+w+int(8*s),int(58*s),x+w+int(32*s),int(134*s)],fill=YEL)
    return im.resize((N,N),Image.LANCZOS)
banner().save(out+'/banner.png'); icon().save(out+'/ic_launcher.png')
