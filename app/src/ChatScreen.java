// SPDX-License-Identifier: Apache-2.0
import javax.microedition.lcdui.*;

/** Fixed dark screen: transcript, discoverable navigation and compact QWERTY composer. */
public final class ChatScreen extends Canvas {
    public interface Actions {
        void send(); void chats(); void history(); void older(); void projects();
        void newChat(); void edit(); void changed(); boolean writable(); boolean hasOlder();
    }
    private final ChatView view;
    private final TextField draft;
    private final Actions actions;
    private final Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    private int width = 480, height = 320, caret;
    // 0 transcript, 1 navigation row, 2 composer, 3 send button.
    private int zone = 2, nav;
    private boolean navigationEntered;
    public ChatScreen(ChatView view, TextField draft, Actions actions) {
        this.view = view; this.draft = draft; this.actions = actions;
        setFullScreenMode(false);
        view.onChange(new Runnable() { public void run() { repaint(); } });
    }
    public void focusMessage(String id) { zone = 0; navigationEntered = true; view.focusMessage(id); repaint(); }
    public void focusInput() { zone = 2; caret = draft.getString().length(); repaint(); }
    public void sync() { caret = Math.min(caret, draft.getString().length()); repaint(); }
    private int navLeft(int i) { int[] proportions = {0, 18, 44, 66, 90, 100}; return width * proportions[i] / 100; }
    private int navAt(int x) { for (int i = 0; i < 4; i++) { if (x < navLeft(i + 1)) { return i; } } return 4; }
    private int composerHeight() { return font.getHeight() + 24; }
    private int navigationHeight() { return font.getHeight() + 12; }
    private int transcriptHeight() { return Math.max(120, height - composerHeight() - navigationHeight()); }
    protected void sizeChanged(int w, int h) { width = w; height = h; view.sizeChanged(w, transcriptHeight()); repaint(); }
    protected void paint(Graphics g) {
        width = getWidth(); height = getHeight();
        // Native screen changes may leave a white background in margins.
        // Own every pixel, including the composer gutter and rounded corners.
        g.setClip(0, 0, width, height); g.setColor(0x131417); g.fillRect(0, 0, width, height);
        view.paint(g, width, transcriptHeight());
        g.setClip(0, 0, width, height);
        int top = transcriptHeight();
        String[] labels = {"Chats", "Verlauf", "Ältere", "Projekt", "+"};
        g.setColor(0x1c1d21); g.fillRect(0, top, width, navigationHeight());
        for (int i = 0; i < labels.length; i++) {
            int x = navLeft(i), cellWidth = navLeft(i + 1) - x;
            g.setColor(zone == 1 && nav == i ? 0x34465c : 0x222429);
            g.fillRoundRect(x + 3, top + 3, cellWidth - 6, navigationHeight() - 6, 6, 6);
            g.setFont(font); g.setColor(i == 2 && !actions.hasOlder() ? 0x555a63 : zone == 1 && nav == i ? 0xe8e8e4 : 0xaeb2ba);
            g.drawString(labels[i], x + (cellWidth - font.stringWidth(labels[i])) / 2, top + 6, Graphics.TOP | Graphics.LEFT);
        }
        int y = top + navigationHeight() + 3, sendWidth = Math.max(70, font.stringWidth("Senden") + 22), boxHeight = font.getHeight() + 16;
        g.setColor(0x26282d); g.fillRoundRect(7, y, width - 14, boxHeight, 10, 10);
        g.setColor(0x3a3d44); g.drawRoundRect(7, y, width - sendWidth - 19, boxHeight - 1, 10, 10);
        String text = draft.getString(); caret = Math.min(caret, text.length());
        String shown = text.substring(0, caret).replace('\n', ' ');
        int begin = 0;
        int available = width - sendWidth - 40;
        while (shown.length() > 0 && font.stringWidth(shown) > available) { shown = shown.substring(1); begin++; }
        g.setFont(font); g.setColor(text.length() == 0 ? 0x8b8e95 : 0xe8e8e4);
        g.setClip(14, y + 4, available + 6, font.getHeight() + 10);
        g.drawString(text.length() == 0 ? "Nachricht schreiben ..." : text.substring(begin).replace('\n', ' '), 16, y + 8, Graphics.TOP | Graphics.LEFT);
        if (zone == 2 && actions.writable()) { g.setColor(0x82b5ed); int cx = 16 + font.stringWidth(shown); g.drawLine(cx, y + 8, cx, y + 8 + font.getHeight()); }
        g.setClip(0, 0, width, height);
        g.setColor(zone == 3 ? 0x82b5ed : 0xe8e8e4); g.fillRoundRect(width - sendWidth - 3, y + 3, sendWidth - 9, boxHeight - 6, 7, 7);
        g.setColor(0x131417); g.drawString("Senden", width - sendWidth - 3 + (sendWidth - 9 - font.stringWidth("Senden")) / 2, y + 8, Graphics.TOP | Graphics.LEFT);
    }
    protected void keyPressed(int key) {
        if (key == 10 || key == 13) {
            if (zone == 2 || zone == 3) { actions.send(); }
            else if (zone == 1) { activateNav(); }
            else { view.activate(); }
            repaint(); return;
        }
        // Printable characters are text, including keys 2/4/6/8, never keypad game actions.
        if ((key >= 32 && key <= 65535) || key == 8 || key == 127) {
            if (!actions.writable()) { return; }
            zone = 2; String value = draft.getString(); caret = Math.min(caret, value.length());
            if (key == 8 || key == 127) {
                if (caret > 0) { draft.setString(value.substring(0, caret - 1) + value.substring(caret)); caret--; }
            } else if (value.length() < 2048) {
                draft.setString(value.substring(0, caret) + (char) key + value.substring(caret)); caret++;
            }
            actions.changed(); repaint(); return;
        }
        int direction;
        try { direction = getGameAction(key); } catch (Exception ignored) { return; }
        if (direction == FIRE) {
            if (zone == 3) { actions.send(); }
            else if (zone == 2) { actions.edit(); }
            else if (zone == 1) { activateNav(); }
            else { view.activate(); }
        } else if (zone == 2) {
            if (direction == UP) { zone = 1; nav = 0; }
            else if (direction == RIGHT) { if (caret < draft.getString().length()) { caret++; } else { zone = 3; } }
            else if (direction == LEFT && caret > 0) { caret--; }
        } else if (zone == 3) {
            if (direction == LEFT) { zone = 2; }
            else if (direction == UP) { zone = 1; nav = 4; }
        } else if (zone == 1) {
            if (direction == LEFT) { nav = Math.max(0, nav - 1); }
            else if (direction == RIGHT) { nav = Math.min(4, nav + 1); }
            else if (direction == DOWN) { zone = 2; }
            else if (direction == UP) { zone = 0; navigationEntered = false; enterTranscript(); }
        } else {
            int[] rect = new int[4];
            if (!navigationEntered) { enterTranscript(); }
            if (!view.traverse(direction, width, transcriptHeight(), rect)) {
                if (direction == DOWN) { zone = 1; nav = 0; view.traverseOut(); }
            }
        }
        repaint();
    }
    private void enterTranscript() { view.focusFromBottom(); navigationEntered = true; }
    protected void keyRepeated(int key) { if (key != 10 && key != 13 && key != -5) { keyPressed(key); } }
    private void activateNav() {
        if (nav == 0) { actions.chats(); } else if (nav == 1) { actions.history(); }
        else if (nav == 2) { actions.older(); } else if (nav == 3) { actions.projects(); } else { actions.newChat(); }
    }
    protected void pointerPressed(int x, int y) {
        int top = transcriptHeight();
        if (y >= top && y < top + navigationHeight()) { zone = 1; nav = navAt(x); activateNav(); }
        else if (y < transcriptHeight()) { zone = 0; view.pointerPressed(x, y); }
        else if (x > width - Math.max(70, font.stringWidth("Senden") + 22) - 6) { zone = 3; actions.send(); }
        else { focusInput(); }
        repaint();
    }
}
