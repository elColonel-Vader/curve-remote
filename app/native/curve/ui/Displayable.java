// SPDX-License-Identifier: Apache-2.0
package curve.ui;
import java.util.Vector;
import net.rim.device.api.ui.MenuItem;
import net.rim.device.api.ui.component.Menu;
import net.rim.device.api.ui.container.MainScreen;
public class Displayable {
 protected MainScreen screen; protected CommandListener listener;
 private final Vector commands=new Vector();
 protected Displayable(String title){screen=new NativeScreen();if(title!=null){screen.setTitle(title);}}
 protected Displayable(){}
 public void addCommand(final Command command){commands.addElement(command);}
 public void setCommandListener(CommandListener value){listener=value;}
 protected void fire(Command command){if(listener!=null){listener.commandAction(command,this);}}
 protected boolean closeScreen(){for(int i=0;i<commands.size();i++){Command c=(Command)commands.elementAt(i);if(c.type==Command.BACK){fire(c);return true;}}Display.owner.notifyDestroyed();return true;}
 protected void menu(Menu menu){for(int i=0;i<commands.size();i++){final Command c=(Command)commands.elementAt(i);menu.add(new MenuItem(c.label,100+c.priority*10,c.priority){public void run(){fire(c);}});}}
 protected class NativeScreen extends MainScreen {
  NativeScreen(){super();}
  NativeScreen(long style){super(style);}
  protected void makeMenu(Menu menu,int instance){Displayable.this.menu(menu);}
  protected boolean onSavePrompt(){return false;}
  public boolean onClose(){return closeScreen();}
 }
}
