// SPDX-License-Identifier: Apache-2.0
package javax.microedition.lcdui;
public class Graphics {
    public static final int TOP=16,LEFT=4;
    private final java.awt.Graphics2D g;
    public Graphics(java.awt.Graphics2D g) { this.g=g; }
    public void setColor(int c) { g.setColor(new java.awt.Color(c)); }
    public void setFont(Font f) { g.setFont(f.awt); }
    public void fillRect(int x,int y,int w,int h) { g.fillRect(x,y,w,h); }
    public void fillRoundRect(int x,int y,int w,int h,int aw,int ah) { g.fillRoundRect(x,y,w,h,aw,ah); }
    public void drawRoundRect(int x,int y,int w,int h,int aw,int ah) { g.drawRoundRect(x,y,w,h,aw,ah); }
    public void drawLine(int x,int y,int x2,int y2) { g.drawLine(x,y,x2,y2); }
    public void fillArc(int x,int y,int w,int h,int start,int arc) { g.fillArc(x,y,w,h,start,arc); }
    public void setClip(int x,int y,int w,int h) { g.setClip(x,y,w,h); }
    public void drawString(String text,int x,int y,int anchor) { g.drawString(text,x,y+g.getFontMetrics().getAscent()); }
}
