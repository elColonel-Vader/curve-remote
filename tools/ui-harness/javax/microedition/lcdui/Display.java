// SPDX-License-Identifier: Apache-2.0
package javax.microedition.lcdui;
public class Display {
 private static final Display INSTANCE=new Display();
 public static Display getDisplay(Object owner){return INSTANCE;}public Displayable current; public void setCurrent(Displayable d){current=d;}public void setCurrent(Alert a,Displayable d){}
 public void setCurrentItem(Item i){} public void callSerially(Runnable r){r.run();}
}
