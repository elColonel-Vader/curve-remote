// SPDX-License-Identifier: Apache-2.0
package curve.ui;
public final class Alert extends Displayable {
 public static final int FOREVER=-2;
 public Alert(String title,String text,Object image,AlertType type){super(title);screen.add(new net.rim.device.api.ui.component.LabelField(text));}
 public void setTimeout(int timeout){}
}
