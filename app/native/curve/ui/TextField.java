// SPDX-License-Identifier: Apache-2.0
package curve.ui;
public final class TextField extends Item {
 public static final int ANY=0,UNEDITABLE=0x20000;
 private final String label;private String value;private final int maximum;private int constraints;
 net.rim.device.api.ui.component.EditField field;
 public TextField(String label,String value,int max,int constraints){this.label=label;this.value=value;maximum=max;this.constraints=constraints;}
 net.rim.device.api.ui.Field nativeField(){if(field==null){field=new net.rim.device.api.ui.component.EditField(label==null?"":label,value,maximum,net.rim.device.api.ui.Field.USE_ALL_WIDTH);field.setEditable((constraints&UNEDITABLE)==0);}return field;}
 public String getString(){return field==null?value:field.getText();}
 public void setString(String text){if(text.length()>maximum)throw new IllegalArgumentException("Text too long");value=text;if(field!=null){field.setText(text);}}
 public void setConstraints(int value){constraints=value;if(field!=null){field.setEditable((constraints&UNEDITABLE)==0);}}
}
