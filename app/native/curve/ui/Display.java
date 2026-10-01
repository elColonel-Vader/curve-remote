// SPDX-License-Identifier: Apache-2.0
package curve.ui;
public final class Display {
 static MIDlet owner; private static Display singleton;
 private Display(MIDlet app){owner=app;}
 public static Display getDisplay(MIDlet app){if(singleton==null){singleton=new Display(app);}return singleton;}
 public void setCurrent(Displayable next){net.rim.device.api.ui.Screen active;while((active=owner.getActiveScreen())!=null){owner.popScreen(active);}owner.pushScreen(next.screen);}
 public void setCurrent(Alert alert,final Displayable back){alert.addCommand(new Command("Zurück",Command.BACK,0));alert.setCommandListener(new CommandListener(){public void commandAction(Command c,Displayable s){setCurrent(back);}});setCurrent(alert);}
 public void callSerially(Runnable runnable){owner.invokeLater(runnable);}
}
