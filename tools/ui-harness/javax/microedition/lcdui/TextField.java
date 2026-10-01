// SPDX-License-Identifier: Apache-2.0
package javax.microedition.lcdui;
public class TextField extends Item {
 public static final int ANY=0,UNEDITABLE=131072;
 private String text;private int max;
 public TextField(String label,String text,int max,int constraints){this.text=text;this.max=max;}
 public void setString(String value){if(value.length()>max)throw new IllegalArgumentException("text length");text=value;}
 public String getString(){return text;}
 public void setConstraints(int c){}
}
