# TV banner (640x360) and icon (192x192) from the wallpaper. Usage: make-art.py <bg.jpg> <selawik_light.ttf> <out dir>
import sys
from PIL import Image, ImageDraw, ImageFont
BG, F, OUT = sys.argv[1:4]
def scrim(im):
    w,h=im.size; g=Image.new('L',(w,1))
    for x in range(w): g.putpixel((x,0),int(180*max(0,1-x/(w*0.75))))
    im.paste((0,0,0),(0,0,w,h),g.resize((w,h)))
    return im
def banner(W=640,H=360,s=3):
    im=scrim(Image.open(BG).convert('RGB').resize((W*s,H*s),Image.LANCZOS)); d=ImageDraw.Draw(im)
    d.text((34*s,108*s),'zune',font=ImageFont.truetype(F,96*s),fill=(255,255,255))
    d.text((38*s,212*s),'sthetic',font=ImageFont.truetype(F,60*s),fill=(255,255,255,))
    return im.resize((W,H),Image.LANCZOS)
def icon(N=192,s=3):
    src=Image.open(BG).convert('RGB'); w,h=src.size
    im=scrim(src.crop(((w-h)//2,0,(w-h)//2+h,h)).resize((N*s,N*s),Image.LANCZOS)); d=ImageDraw.Draw(im)
    d.text((22*s,40*s),'z',font=ImageFont.truetype(F,120*s),fill=(255,255,255))
    return im.resize((N,N),Image.LANCZOS)
banner().save(OUT+'/banner.png'); icon().save(OUT+'/ic_launcher.png')
