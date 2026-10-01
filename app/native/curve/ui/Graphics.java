// SPDX-License-Identifier: Apache-2.0
package curve.ui;
public final class Graphics {
 public static final int TOP=16,LEFT=4;
 private final net.rim.device.api.ui.Graphics g; private boolean clipped;
 public Graphics(net.rim.device.api.ui.Graphics graphics){g=graphics;}
 public void setColor(int color){g.setColor(color);} public void setFont(Font font){g.setFont(font.nativeFont);}
 public void setClip(int x,int y,int w,int h){int color=g.getColor();net.rim.device.api.ui.Font font=g.getFont();if(clipped){g.popContext();}g.pushContext(x,y,w,h,0,0);clipped=true;g.setColor(color);g.setFont(font);}
 public void finish(){if(clipped){g.popContext();clipped=false;}}
 public void fillRect(int x,int y,int w,int h){g.fillRect(x,y,w,h);}
 public void fillRoundRect(int x,int y,int w,int h,int a,int b){g.fillRoundRect(x,y,w,h,a,b);}
 public void drawRoundRect(int x,int y,int w,int h,int a,int b){g.drawRoundRect(x,y,w,h,a,b);}
 public void drawLine(int x,int y,int a,int b){g.drawLine(x,y,a,b);}
 public void drawString(String s,int x,int y,int anchor){g.drawText(s,x,y);}
 public void fillArc(int x,int y,int w,int h,int start,int angle){g.fillArc(x,y,w,h,start,angle);}
}
