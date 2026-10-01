// SPDX-License-Identifier: Apache-2.0
package javax.microedition.lcdui;
public abstract class Canvas extends Displayable {
 public static final int UP=1,LEFT=2,RIGHT=5,DOWN=6,FIRE=8;
 public void setFullScreenMode(boolean full){} public void repaint(){}
 public int getWidth(){return 480;}public int getHeight(){return Integer.getInteger("midp.height",320).intValue();}
 public int getGameAction(int key){if(key==-1)return UP;if(key==-2)return DOWN;if(key==-3)return LEFT;if(key==-4)return RIGHT;if(key==-5)return FIRE;return key;}
 protected abstract void paint(Graphics g);
}
