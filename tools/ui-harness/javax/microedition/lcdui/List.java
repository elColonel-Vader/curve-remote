// SPDX-License-Identifier: Apache-2.0
package javax.microedition.lcdui;
public class List extends Displayable {
 public static final int IMPLICIT=1;public static final Command SELECT_COMMAND=new Command("Select",1,0);
 public final java.util.Vector entries=new java.util.Vector(); private int selected;
 public List(String title,int mode){}public int append(String text,Object image){entries.addElement(text);return entries.size()-1;}public int getSelectedIndex(){return selected;}
 public void setSelectedIndex(int i,boolean value){selected=i;}
}
