// SPDX-License-Identifier: Apache-2.0
package curve.ui;
public final class TextBox extends Displayable {
 private final TextField field;
 public TextBox(String title,String text,int max,int constraints){super(title);field=new TextField(null,text,max,constraints);screen.add(field.nativeField());}
 public String getString(){return field.getString();}
}
