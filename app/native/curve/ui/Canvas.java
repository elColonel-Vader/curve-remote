// SPDX-License-Identifier: Apache-2.0
package curve.ui;
import net.rim.device.api.ui.Field;
public abstract class Canvas extends Displayable {
 public static final int UP=1,LEFT=2,RIGHT=5,DOWN=6,FIRE=8;
 private Field field; private int width=480,height=360;
 protected Canvas(){screen=new NativeScreen(net.rim.device.api.ui.Manager.NO_VERTICAL_SCROLL|net.rim.device.api.ui.Manager.NO_HORIZONTAL_SCROLL);field=new Field(Field.FOCUSABLE|Field.USE_ALL_WIDTH|Field.USE_ALL_HEIGHT){
  protected void layout(int w,int h){setExtent(w,h);if(width!=w||height!=h){width=w;height=h;sizeChanged(w,h);}}
  protected void paint(net.rim.device.api.ui.Graphics nativeGraphics){Graphics g=new Graphics(nativeGraphics);try{Canvas.this.paint(g);}finally{g.finish();}}
  protected void drawFocus(net.rim.device.api.ui.Graphics g,boolean on){}
  protected boolean keyChar(char key,int status,int time){if(key==27){closeScreen();}else{keyPressed(key);}return true;}
  protected boolean navigationClick(int status,int time){keyPressed(-5);return true;}
  protected boolean navigationMovement(int dx,int dy,int status,int time){if(Math.abs(dy)>=Math.abs(dx)&&dy!=0){keyPressed(dy<0?-1:-2);}else if(dx!=0){keyPressed(dx<0?-3:-4);}return true;}
 };screen.add(field);}
 public void setFullScreenMode(boolean value){}
 public int getWidth(){return width;} public int getHeight(){return height;}
 public void repaint(){if(screen!=null){screen.invalidate();}}
 public int getGameAction(int key){return action(key);}
 static int action(int key){if(key==-1)return UP;if(key==-2)return DOWN;if(key==-3)return LEFT;if(key==-4)return RIGHT;if(key==-5)return FIRE;throw new IllegalArgumentException("Key has no navigation action");}
 protected abstract void paint(Graphics g);protected void keyPressed(int key){}protected void keyRepeated(int key){}protected void pointerPressed(int x,int y){}protected void sizeChanged(int w,int h){}
}
