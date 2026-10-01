// SPDX-License-Identifier: Apache-2.0
package curve.ui;
public final class StringItem extends Item {
 private final net.rim.device.api.ui.component.LabelField field;
 private final String label;
 public StringItem(String label,String text){this.label=label;field=new net.rim.device.api.ui.component.LabelField(label+": "+text);}
 public void setText(String text){field.setText(label+": "+text);}
 net.rim.device.api.ui.Field nativeField(){return field;}
}
