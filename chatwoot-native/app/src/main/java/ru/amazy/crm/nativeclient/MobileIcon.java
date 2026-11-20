package ru.amazy.crm.nativeclient;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Crisp 24-unit, consistently weighted line icons, independent of system emoji. */
final class MobileIcon extends Drawable {
    private final String kind;
    private final Paint p = new Paint(3);
    private final int size;
    MobileIcon(String kind, int color, int size) {
        this.kind = kind; this.size = size;
        p.setColor(color); p.setStrokeWidth(1.7f);
        p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        p.setStyle(Paint.Style.STROKE);
    }
    private void path(Canvas c, float... pts) {
        Path t = new Path(); t.moveTo(pts[0], pts[1]);
        for (int i=2;i<pts.length;i+=2) t.lineTo(pts[i],pts[i+1]);
        c.drawPath(t,p);
    }
    private void round(Canvas c,float l,float t,float r,float b,float radius) {
        c.drawRoundRect(new RectF(l,t,r,b),radius,radius,p);
    }
    @Override public void draw(Canvas c) {
        Rect b=getBounds(); c.save(); c.translate(b.left,b.top); c.scale(b.width()/24f,b.height()/24f);
        p.setStyle(Paint.Style.STROKE);
        switch(kind) {
            case "search": c.drawCircle(10.5f,10.5f,5.8f,p); path(c,15,15,20,20); break;
            case "filter": path(c,4,6,20,6); path(c,7,12,17,12); path(c,10,18,14,18); break;
            case "back": path(c,14.5f,5.5f,8,12,14.5f,18.5f); break;
            case "chevron": path(c,9,10,12,13,15,10); break;
            case "refresh": c.drawArc(new RectF(4,4,20,20),-35,295,false,p); path(c,20,4,20,10,14,10); break;
            case "inbox": path(c,3,13,6,5,18,5,21,13,21,20,3,20,3,13,8,13,10,16,14,16,16,13,21,13); break;
            case "chat": case "chat-filled":
                Path bubble = new Path(); bubble.moveTo(6,4); bubble.lineTo(18,4); bubble.quadTo(21,4,21,7); bubble.lineTo(21,15); bubble.quadTo(21,18,18,18); bubble.lineTo(10,18); bubble.lineTo(5,21); bubble.lineTo(6,18); bubble.quadTo(3,18,3,15); bubble.lineTo(3,7); bubble.quadTo(3,4,6,4);
                if(kind.endsWith("filled"))p.setStyle(Paint.Style.FILL);
                c.drawPath(bubble,p); break;
            case "settings":
                c.drawCircle(12,12,7.2f,p); c.drawCircle(12,12,2.6f,p);
                for(int i=0;i<8;i++){double a=i*Math.PI/4; c.drawLine(12+(float)Math.cos(a)*7.2f,12+(float)Math.sin(a)*7.2f,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,p);} break;
            case "more": p.setStyle(Paint.Style.FILL); for(int i=0;i<3;i++)c.drawCircle(5+7*i,12,1.7f,p); break;
            case "plus": path(c,12,5,12,19); path(c,5,12,19,12); break;
            case "mic": round(c,9,3,15,15,3); c.drawArc(new RectF(5.5f,7,18.5f,19),0,180,false,p); path(c,12,19,12,22); path(c,9,22,15,22); break;
            case "stop": p.setStyle(Paint.Style.FILL); round(c,6,6,18,18,2); break;
            case "bolt": path(c,13,2,5,14,11,14,10,22,19,10,13,10,13,2); break;
            case "send": path(c,12,19,12,5); path(c,5,12,12,5,19,12); break;
            case "check": path(c,5,12,10,17,19,7); break;
            case "close": path(c,6,6,18,18); path(c,18,6,6,18); break;
            case "lock": round(c,6,10,18,21,2); c.drawArc(new RectF(8,3,16,15),180,180,false,p); break;
            default: c.drawCircle(12,12,8,p); break;
        }
        c.restore();
    }
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    @Override public void setAlpha(int a){p.setAlpha(a);}
    @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
