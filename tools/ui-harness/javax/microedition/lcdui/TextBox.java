// SPDX-License-Identifier: Apache-2.0
package javax.microedition.lcdui;
public class TextBox extends Displayable {private String value;public TextBox(String title,String text,int max,int constraints){value=text;}public String getString(){return value;}}
