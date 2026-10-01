// SPDX-License-Identifier: Apache-2.0
package javax.microedition.lcdui;
public abstract class CustomItem extends Item {
    protected CustomItem(String label) { }
    public void repaint() { }
    public int getGameAction(int key) { return key; }
}
