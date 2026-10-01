// SPDX-License-Identifier: Apache-2.0
package curve.ui;
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.FieldChangeListener;
import net.rim.device.api.ui.component.ButtonField;
public final class List extends Displayable {
 public static final int IMPLICIT=3;public static final Command SELECT_COMMAND=new Command("Öffnen",Command.ITEM,0);
 private int count,selected=-1;
 public List(String title,int type){super(title);}
 public void append(String text,Object image){final int index=count++;ButtonField button=new ButtonField(text,Field.USE_ALL_WIDTH|ButtonField.CONSUME_CLICK);button.setChangeListener(new FieldChangeListener(){public void fieldChanged(Field f,int context){selected=index;fire(SELECT_COMMAND);}});screen.add(button);}
 public int getSelectedIndex(){return selected;}
}
