// SPDX-License-Identifier: Apache-2.0
package curve.ui;
public abstract class MIDlet extends net.rim.device.api.ui.UiApplication {
 protected abstract void startApp(); protected abstract void pauseApp(); protected abstract void destroyApp(boolean unconditional);
 public final void launch(){startApp();enterEventDispatcher();}
 public final void notifyDestroyed(){destroyApp(true);System.exit(0);}
}
