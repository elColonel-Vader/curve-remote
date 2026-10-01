// SPDX-License-Identifier: Apache-2.0
import java.util.Vector;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.CustomItem;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/** Dark, trackpad-navigable transcript. Native text entry lives below this item. */
public final class ChatView extends CustomItem {
    public interface Actions { void chats(); void newChat(); void reply(ChatMessage message); }
    private final Actions actions;
    private final Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    private final Font bold = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
    private final Vector messages = new Vector();
    private final Vector layouts = new Vector();
    private String title = "Neuer Chat", project = "bb_curve", status = "Bereit", quote = "";
    private int width = 440, height = 246, scroll, total, selected = -2;
    private boolean focused;
    private Runnable change;
    public void onChange(Runnable action) { change = action; }
    private void redraw() { repaint(); if (change != null) { change.run(); } }
    private final int TITLE_HEIGHT = bold.getHeight() + font.getHeight() + 14;
    private int HEADER = TITLE_HEIGHT;
    public int headerHeight() { return TITLE_HEIGHT; }
    public void navigationHeight(int value) { HEADER = TITLE_HEIGHT + value; layout(); }
    public void focusMessage(String id) {
        for (int i = 0; i < messages.size(); i++) {
            if (message(i).id.equals(id)) { selected = i; focused = true; Layout l = (Layout) layouts.elementAt(i); scroll = Math.min(maxScroll(), l.top); redraw(); return; }
        }
    }
    private final int FOOTER = 0;
    private final int QUOTE_HEIGHT = font.getHeight() + 10;
    private static final int BG = 0x131417, FG = 0xe8e8e4, MUTED = 0x8b8e95, BLUE = 0x82b5ed;

    private static final class Layout {
        ChatMessage message; String[] lines; int top, height, x, width;
    }

    public ChatView(Actions actions) { super(null); this.actions = actions; }
    protected int getMinContentWidth() { return 180; }
    protected int getMinContentHeight() { return 180; }
    protected int getPrefContentWidth(int h) { return 480; }
    protected int getPrefContentHeight(int w) { return 246; }
    protected void sizeChanged(int w, int h) {
        boolean bottom = atBottom(); width = w; height = h; layout();
        if (bottom) { scroll = maxScroll(); } redraw();
    }
    private int viewport() { return Math.max(50, height - HEADER - FOOTER - (quote.length() > 0 ? QUOTE_HEIGHT : 0)); }
    private int maxScroll() { return Math.max(0, total - viewport()); }
    public boolean atBottom() { return scroll >= maxScroll() - 8; }
    public int count() { return messages.size(); }
    public ChatMessage message(int index) { return (ChatMessage) messages.elementAt(index); }
    public void header(String name, String folder, String state) {
        title = name; int slash = folder.lastIndexOf('/'); project = slash >= 0 ? folder.substring(slash + 1) : folder;
        status = state; redraw();
    }
    public void status(String value) { status = value; redraw(); }
    public void quote(String value) { quote = value; scroll = Math.min(scroll, maxScroll()); redraw(); }
    public void clear() { messages.removeAllElements(); selected = -2; scroll = 0; layout(); redraw(); }
    public void pending(String text, String cited) {
        ChatMessage message = new ChatMessage("local-pending", true, text, cited, false); message.pending = true;
        messages.addElement(message); layout(); scroll = maxScroll(); redraw();
    }
    public void removePending() {
        for (int i = messages.size() - 1; i >= 0; i--) { if (message(i).pending) { messages.removeElementAt(i); } }
        layout(); scroll = Math.min(scroll, maxScroll()); redraw();
    }
    public void merge(ChatMessage[] page, boolean older) {
        boolean bottom = atBottom(); int before = total;
        String selectedId = selected >= 0 && selected < messages.size() ? message(selected).id : "";
        Vector added = new Vector();
        for (int i = 0; i < page.length; i++) {
            boolean found = false;
            for (int j = 0; j < messages.size(); j++) {
                if (message(j).id.equals(page[i].id)) { messages.setElementAt(page[i], j); found = true; break; }
            }
            if (!found) { added.addElement(page[i]); }
        }
        if (older) {
            for (int i = added.size() - 1; i >= 0; i--) { messages.insertElementAt(added.elementAt(i), 0); }
        } else { for (int i = 0; i < added.size(); i++) { messages.addElement(added.elementAt(i)); } }
        layout();
        if (older) { scroll += total - before; }
        else if (bottom) { scroll = maxScroll(); }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        if (selectedId.length() > 0) {
            for (int i = 0; i < messages.size(); i++) { if (message(i).id.equals(selectedId)) { selected = i; break; } }
        }
        redraw();
    }
    private void layout() {
        layouts.removeAllElements(); total = 10;
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = message(i); Layout l = new Layout(); l.message = message;
            l.width = Math.max(80, Math.min(width * (message.user ? 78 : 88) / 100, width - 40));
            l.x = message.user ? width - l.width - 10 : 30;
            String text = message.text;
            if (message.quote.length() > 0) { text = "> " + message.quote + "\n\n" + text; }
            if (message.truncated) { text += "\n[Nachricht gekürzt]"; }
            l.lines = wrap(text, l.width - 20);
            l.top = total; l.height = l.lines.length * font.getHeight() + 24 + (message.pending ? font.getHeight() : 0);
            layouts.addElement(l); total += l.height + 8;
        }
    }
    private String[] wrap(String text, int available) {
        Vector result = new Vector(); int begin = 0;
        while (begin < text.length()) {
            int end = begin, space = -1;
            while (end < text.length() && text.charAt(end) != '\n') {
                if (font.substringWidth(text, begin, end - begin + 1) > available && end > begin) { break; }
                if (text.charAt(end) == ' ') { space = end; } end++;
            }
            boolean newline = end < text.length() && text.charAt(end) == '\n';
            if (!newline && end < text.length() && space > begin) { end = space; }
            if (end == begin && !newline) { end++; }
            result.addElement(text.substring(begin, end));
            begin = end;
            if (begin < text.length() && (text.charAt(begin) == '\n' || text.charAt(begin) == ' ')) { begin++; }
        }
        if (result.size() == 0) { result.addElement(""); }
        String[] lines = new String[result.size()]; result.copyInto(lines); return lines;
    }
    private String fit(String text, Font f, int available) {
        if (f.stringWidth(text) <= available) { return text; }
        while (text.length() > 0 && f.stringWidth(text + "...") > available) { text = text.substring(0, text.length() - 1); }
        return text + "...";
    }
    protected void paint(Graphics g, int w, int h) {
        if (width != w || height != h || layouts.size() != messages.size()) { width = w; height = h; layout(); }
        g.setColor(BG); g.fillRect(0, 0, w, h);
        if (focused && selected == -2) { g.setColor(0x26282d); g.fillRoundRect(4, 3, w - 70, TITLE_HEIGHT - 7, 8, 8); }
        String shownStatus = fit(status, font, w / 3 - 18);
        int statusWidth = font.stringWidth(shownStatus) + 15, statusX = w - statusWidth - 10;
        g.setFont(bold); g.setColor(FG);
        g.drawString(fit(title, bold, statusX - 22), 12, 5, Graphics.TOP | Graphics.LEFT);
        g.setFont(font); g.setColor(MUTED);
        g.drawString(fit(project, font, w - 76), 12, bold.getHeight() + 7, Graphics.TOP | Graphics.LEFT);
        int buttonSize = font.getHeight() + 4, buttonX = w - buttonSize - 10, buttonY = bold.getHeight() + 5;
        g.setColor(focused && selected == -1 ? BLUE : 0x26282d); g.fillRoundRect(buttonX, buttonY, buttonSize, buttonSize, 7, 7);
        int centerX = buttonX + buttonSize / 2, centerY = buttonY + buttonSize / 2;
        g.setColor(FG); g.drawLine(centerX, centerY - 7, centerX, centerY + 7); g.drawLine(centerX - 7, centerY, centerX + 7, centerY);
        int statusY = 5 + (bold.getHeight() - font.getHeight()) / 2;
        g.setFont(font); g.setColor(status.equals("Bereit") ? 0x85c9a4 : BLUE);
        g.fillArc(statusX, statusY + font.getHeight() / 2 - 3, 6, 6, 0, 360);
        g.drawString(shownStatus, statusX + 15, statusY, Graphics.TOP | Graphics.LEFT);
        g.setColor(0x24262b); g.drawLine(0, TITLE_HEIGHT - 1, w, TITLE_HEIGHT - 1);
        g.setClip(0, HEADER, w, viewport());
        if (messages.size() == 0) {
            g.setFont(font); g.setColor(MUTED); g.drawString("Woran soll Codex arbeiten?", 18, HEADER + 22, Graphics.TOP | Graphics.LEFT);
            g.drawString("Schreibe unten eine Nachricht.", 18, HEADER + 43, Graphics.TOP | Graphics.LEFT);
        }
        for (int i = 0; i < layouts.size(); i++) {
            Layout l = (Layout) layouts.elementAt(i); int y = HEADER + l.top - scroll;
            if (y + l.height < HEADER || y > HEADER + viewport()) { continue; }
            if (l.message.user) { g.setColor(0x26282d); g.fillRoundRect(l.x, y, l.width, l.height, 12, 12); }
            else {
                g.setColor(FG); g.fillRoundRect(9, y + 4, 16, 16, 5, 5); g.setColor(BG); terminal(g, 12, y + 8);
            }
            if (focused && selected == i) { g.setColor(BLUE); g.drawRoundRect(l.x, y, l.width - 1, l.height - 1, 10, 10); }
            g.setFont(font); g.setColor(l.message.user ? FG : 0xd6d6d2);
            for (int j = 0; j < l.lines.length; j++) { g.drawString(l.lines[j], l.x + 10, y + 10 + j * font.getHeight(), Graphics.TOP | Graphics.LEFT); }
            if (l.message.pending) { g.setColor(MUTED); g.drawString("Wird gesendet ...", l.x + 10, y + l.height - font.getHeight() - 5, Graphics.TOP | Graphics.LEFT); }
        }
        if (maxScroll() > 0) {
            g.setColor(0x424650); int thumb = Math.max(10, viewport() * viewport() / Math.max(total, 1));
            g.fillRect(w - 4, HEADER + scroll * (viewport() - thumb) / maxScroll(), 2, thumb);
        }
        g.setClip(0, 0, w, h); int footer = HEADER + viewport();
        if (quote.length() > 0) {
            g.setColor(0x1c1d21); g.fillRect(0, footer, w, QUOTE_HEIGHT); g.setColor(BLUE); g.fillRect(7, footer + 3, 2, QUOTE_HEIGHT - 6);
            g.setFont(font); g.setColor(MUTED); g.drawString(fit("Antwort: " + quote, font, w - 30), 15, footer + 5, Graphics.TOP | Graphics.LEFT); footer += QUOTE_HEIGHT;
        }

    }
    private void terminal(Graphics g, int x, int y) {
        g.drawLine(x, y, x + 4, y + 3); g.drawLine(x + 4, y + 3, x, y + 6);
        g.drawLine(x + 6, y + 6, x + 10, y + 6);
    }
    public void focusFromBottom() {
        focused = true; selected = -2;
        for (int i = 0; i < layouts.size(); i++) {
            Layout l = (Layout) layouts.elementAt(i);
            if (l.top < scroll + viewport()) { selected = i; }
        }
        redraw();
    }
    protected boolean traverse(int direction, int viewWidth, int viewHeight, int[] visible) {
        focused = true;
        if (direction == Canvas.DOWN || direction == Canvas.RIGHT) {
            if (selected >= 0 && selected < layouts.size()) {
                Layout l = (Layout) layouts.elementAt(selected);
                if (l.top + l.height > scroll + viewport() && l.top <= scroll + viewport()) {
                    scroll = Math.min(maxScroll(), scroll + font.getHeight() * 3); redraw(); return true;
                }
            }
            if (selected >= messages.size() - 1) { focused = false; redraw(); return false; }
            selected++;
        } else if (direction == Canvas.UP || direction == Canvas.LEFT) {
            if (selected >= 0 && selected < layouts.size()) {
                Layout l = (Layout) layouts.elementAt(selected);
                if (l.top < scroll) { scroll = Math.max(0, scroll - font.getHeight() * 3); redraw(); return true; }
            }
            if (selected <= -2) { focused = false; redraw(); return false; }
            selected--;
        } else if (selected >= messages.size()) { selected = -2; }
        if (selected >= 0 && selected < layouts.size()) {
            Layout l = (Layout) layouts.elementAt(selected);
            if (l.top < scroll) { scroll = l.top; }
            else if (l.top >= scroll + viewport()) { scroll = l.top; }
            scroll = Math.min(scroll, maxScroll());
        }
        visible[0] = 0; visible[1] = 0; visible[2] = width; visible[3] = height;
        redraw(); return true;
    }
    protected void traverseOut() { focused = false; redraw(); }
    protected void keyPressed(int key) {
        int action;
        try { action = getGameAction(key); } catch (Exception ignored) { return; }
        if (action == Canvas.FIRE) { activate(); }
    }
    public void activate() {
        if (selected == -2) { actions.chats(); }
        else if (selected == -1) { actions.newChat(); }
        else if (selected < messages.size()) { actions.reply(message(selected)); }
    }
    protected void pointerPressed(int x, int y) {
        if (y < HEADER) { selected = x > width - 50 ? -1 : -2; activate(); return; }
        for (int i = 0; i < layouts.size(); i++) {
            Layout l = (Layout) layouts.elementAt(i);
            if (y - HEADER + scroll >= l.top && y - HEADER + scroll < l.top + l.height) { selected = i; focused = true; redraw(); return; }
        }
    }
}
