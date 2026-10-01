// SPDX-License-Identifier: Apache-2.0
import java.io.*;
import java.util.Vector;
import javax.microedition.io.*;
import javax.microedition.lcdui.*;
import javax.microedition.rms.RecordStore;
import javax.microedition.midlet.MIDlet;

/** Curve Remote: stable conversations, dark transcript, native keyboard input. */
public final class CurveProbe extends MIDlet implements CommandListener, ChatView.Actions {
    private final ChatView view = new ChatView(this);
    private final TextField input = new TextField(null, "", 2048, TextField.ANY);
    private final ChatScreen chat = new ChatScreen(view, input, new ChatScreen.Actions() {
        public void send() { commandAction(send, chat); }
        public void chats() { commandAction(chats, chat); }
        public void history() { showHistory(); }
        public void older() { commandAction(older, chat); }
        public void projects() { commandAction(projects, chat); }
        public void newChat() { CurveProbe.this.newChat(); }
        public void edit() { editDraft(); }
        public void changed() { rememberDraft(); }
        public boolean writable() { return writableChat; }
        public boolean hasOlder() { return historyCursor.length() > 0; }
    });
    private TextBox editor;
    private final Command acceptEdit = new Command("Übernehmen", Command.OK, 0);
    private final Command editText = new Command("Text bearbeiten", Command.SCREEN, 2);
    private final Form settings = new Form("Einstellungen");
    private final TextField host = new TextField("Mac IP", "192.168.1.10", 64, TextField.ANY);
    private final Command send = new Command("Senden", Command.SCREEN, 0);
    private final Command chats = new Command("Chats", Command.SCREEN, 1);
    private final Command quoteCommand = new Command("Antworten", Command.ITEM, 1);
    private final Command unquote = new Command("Zitat entfernen", Command.SCREEN, 2);
    private final Command newChat = new Command("Neuer Chat", Command.SCREEN, 3);
    private final Command refresh = new Command("Verlauf aktualisieren", Command.SCREEN, 4);
    private final Command older = new Command("Ältere Nachrichten", Command.SCREEN, 5);
    private final Command projects = new Command("Projekt wählen", Command.SCREEN, 6);
    private final Command preferences = new Command("Einstellungen", Command.SCREEN, 7);
    private final Command recover = new Command("Ungesendeten Text laden", Command.SCREEN, 8);
    private final Command exit = new Command("Beenden", Command.EXIT, 8);
    private final Command back = new Command("Zurueck", Command.BACK, 0);
    private final Command more = new Command("Weitere Chats", Command.SCREEN, 1);
    private final Command wifi = new Command("WLAN testen", Command.SCREEN, 1);
    private final StringItem connectionStatus = new StringItem("Verbindung", "Curve Remote 0.7.1 / lokales WLAN");
    private List selection, messageHistory;
    private String[] historyIds;
    private String[] selectedIds, selectedFolders, selectedTitles;
    private String nextCursor = "", historyCursor = "", threadId = "", title = "Neuer Chat";
    private String cwd = "", quoteId = "", quoteText = "";
    private boolean projectSelection, writableChat = true, started, loadedOlder;
    private final Vector draftIds = new Vector(), drafts = new Vector(), quoteIds = new Vector(), quotes = new Vector();
    private volatile Connection active;
    private volatile int generation;
    private volatile boolean busy;
    private String submitted = "", submittedQuote = "", submittedQuoteId = "";

    public CurveProbe() {
        chat.addCommand(editText);
        chat.addCommand(send); chat.addCommand(chats); chat.addCommand(newChat); chat.addCommand(refresh);
        chat.addCommand(older); chat.addCommand(unquote); chat.addCommand(projects); chat.addCommand(preferences); chat.addCommand(recover); chat.addCommand(exit);
        chat.setCommandListener(this);
        view.addCommand(quoteCommand);
        view.setItemCommandListener(new ItemCommandListener() {
            public void commandAction(Command command, Item item) { view.activate(); }
        });
        settings.append(host); settings.append(connectionStatus); settings.addCommand(wifi); settings.addCommand(back); settings.setCommandListener(this);
        loadState(); updateHeader("Bereit");
    }
    protected void startApp() {
        Display.getDisplay(this).setCurrent(chat);
        if (!started) { started = true; request("HISTORY", threadId, "", "", true); }
    }
    // A permission dialog may pause the MIDlet while Connector.open is waiting.
    // Keep that request alive; cancelling here left the status stuck at Verbinde.
    protected void pauseApp() { saveState(); }
    protected void destroyApp(boolean unconditional) { cancel(); saveState(); }
    private synchronized void cancel() {
        generation++; busy = false; close(active); active = null;
        boolean interrupted = submitted.length() > 0;
        failedSend();
        if (interrupted) { updateHeader("Verlauf prüfen"); }
    }
    private static void close(Connection connection) { if (connection != null) { try { connection.close(); } catch (Exception ignored) { } } }
    private void updateHeader(String status) { view.header(title, cwd, writableChat ? status : "Nur lesen"); view.quote(quoteText); chat.sync(); }
    public void chats() { commandAction(chats, chat); }
    public void newChat() { commandAction(newChat, chat); }
    public void reply(ChatMessage message) {
        if (!writableChat) { return; }
        if (message.user || message.pending) { view.status("Codex-Nachricht wählen"); return; }
        quoteId = message.id; quoteText = message.text.substring(0, Math.min(180, message.text.length())).replace('\n', ' ');
        view.quote(quoteText); chat.focusInput(); saveState();
    }
    private void showHistory() {
        messageHistory = new List("Verlauf · " + title, List.IMPLICIT);
        historyIds = new String[view.count() + 2];
        for (int i = 0; i < view.count(); i++) {
            ChatMessage message = view.message(i);
            String text = message.text.replace('\n', ' ');
            messageHistory.append((message.user ? "Du: " : "Codex: ") + text.substring(0, Math.min(90, text.length())), null);
            historyIds[i] = message.id;
        }
        messageHistory.append(historyCursor.length() > 0 ? "Ältere Nachrichten laden" : "Keine älteren Nachrichten verfügbar", null);
        historyIds[view.count()] = "@older";
        messageHistory.append("Verlauf aktualisieren", null); historyIds[view.count() + 1] = "@refresh";
        messageHistory.addCommand(back); messageHistory.setCommandListener(this);
        Display.getDisplay(this).setCurrent(messageHistory);
    }
    private void editDraft() {
        if (!writableChat) { return; }
        editor = new TextBox("Nachricht bearbeiten", input.getString(), 2048, TextField.ANY);
        editor.addCommand(acceptEdit); editor.addCommand(back); editor.setCommandListener(this);
        Display.getDisplay(this).setCurrent(editor);
    }
    public void commandAction(Command command, Displayable screen) {
        if (command == acceptEdit && screen == editor) { input.setString(editor.getString()); chat.focusInput(); saveState(); Display.getDisplay(this).setCurrent(chat); return; }
        if (command == editText) { editDraft(); return; }
        if (command == exit) { cancel(); saveState(); notifyDestroyed(); return; }
        if (command == back) { saveState(); chat.focusInput(); Display.getDisplay(this).setCurrent(chat); return; }
        if (command == preferences) { Display.getDisplay(this).setCurrent(settings); return; }
        if (command == unquote) { quoteId = ""; quoteText = ""; view.quote(""); saveState(); return; }
        if (command == recover) {
            int index = draftIds.indexOf(threadId + "/ungesendet");
            if (index < 0 || ((String) drafts.elementAt(index)).length() == 0) { view.status("Kein ungesendeter Text"); return; }
            String text = (String) drafts.elementAt(index), citedId = (String) quoteIds.elementAt(index), cited = (String) quotes.elementAt(index);
            storeDraft(threadId + "/ungesendet", input.getString(), quoteId, quoteText);
            input.setString(text); quoteId = citedId; quoteText = cited; view.quote(cited); saveState(); return;
        }
        if (command == List.SELECT_COMMAND && screen == messageHistory) {
            int index = messageHistory.getSelectedIndex(); if (index < 0 || index >= historyIds.length) { return; }
            String id = historyIds[index];
            if (id.equals("@older")) { Display.getDisplay(this).setCurrent(chat); commandAction(older, chat); }
            else if (id.equals("@refresh")) { Display.getDisplay(this).setCurrent(chat); commandAction(refresh, chat); }
            else { Display.getDisplay(this).setCurrent(chat); chat.focusMessage(id); }
            return;
        }
        if (busy) { view.status("Codex arbeitet; Eingabe möglich"); return; }
        if (command == send) {
            if (!writableChat) { view.status("Nur lesen; Menü: Neuer Chat"); return; }
            String text = input.getString().trim(); if (text.length() == 0) { return; }
            submitted = text; submittedQuote = quoteText; submittedQuoteId = quoteId;
            view.pending(text, quoteText); input.setString(""); quoteId = ""; quoteText = ""; view.quote("");
            request("SEND", threadId, submittedQuoteId, text, true); return;
        }
        if (command == chats || command == more) { request("LIST", command == more ? nextCursor : "", "", "", false); return; }
        if (command == projects) { request("PROJECTS", "", "", "", false); return; }
        if (command == newChat) { rememberDraft(); request("NEW", cwd, "", "Neuer Chat", true); return; }
        if (command == refresh || command == older) {
            if (command == older && historyCursor.length() == 0) { view.status("Keine älteren Nachrichten"); return; }
            request("HISTORY", threadId, command == older ? historyCursor : "", "", true); return;
        }
        if (command == wifi) { probe(); return; }
        if (command == List.SELECT_COMMAND && screen == selection) {
            int index = selection.getSelectedIndex(); if (index < 0) { return; }
            rememberDraft();
            if (projectSelection) { request("NEW", selectedFolders[index], "", "Neuer Chat", true); }
            else {
                title = selectedTitles[index]; updateHeader("Verlauf laden");
                request("HISTORY", selectedIds[index], "", "", true);
            }
        }
    }
    private void request(final String operation, final String target, final String auxiliary, final String text, final boolean structured) {
        final String address = host.getString().trim();
        if (address.length() == 0 || address.indexOf('/') >= 0 || address.indexOf(';') >= 0) { failedSend(); updateHeader("Mac-IP prüfen"); return; }
        busy = true; final int attempt = ++generation; updateHeader(operation.equals("SEND") ? "Wird gesendet" : "Verbinde ...");
        if (structured) { Display.getDisplay(this).setCurrent(chat); }
        new Thread(new Runnable() {
            public void run() {
                StreamConnection connection = null;
                try {
                    connection = (StreamConnection) Connector.open("socket://" + address + ":8767;deviceside=true;interface=wifi", Connector.READ_WRITE, true);
                    synchronized (CurveProbe.this) { if (attempt != generation) { close(connection); return; } active = connection; }
                    DataOutputStream output = new DataOutputStream(connection.openOutputStream());
                    DataInputStream stream = new DataInputStream(connection.openInputStream());
                    output.write((structured ? "CURVE03\n" : "CURVE02\n").getBytes("UTF-8"));
                    writeFrame(output, Pairing.TOKEN); writeFrame(output, operation); writeFrame(output, target);
                    if (structured) { writeFrame(output, auxiliary); } writeFrame(output, text); output.flush();
                    int result = stream.readUnsignedByte();
                    while (result == 2) {
                        final String progress = readFrame(stream, 256);
                        Display.getDisplay(CurveProbe.this).callSerially(new Runnable() {
                            public void run() { if (attempt == generation) { updateHeader(progress); } }
                        }); result = stream.readUnsignedByte();
                    }
                    if (result != 0) { throw new IOException(readFrame(stream, 24000)); }
                    final ChatPacket packet;
                    final String listReply;
                    if (structured) { packet = readPacket(stream); listReply = ""; }
                    else { packet = null; listReply = readFrame(stream, 24000); readFrame(stream, 256); }
                    Display.getDisplay(CurveProbe.this).callSerially(new Runnable() {
                        public void run() {
                            if (attempt != generation) { return; }
                            busy = false; active = null;
                            if (packet != null) { apply(packet, operation, auxiliary.length() > 0 && operation.equals("HISTORY")); }
                            else { showList(operation, listReply); }
                        }
                    });
                } catch (final Exception error) {
                    Display.getDisplay(CurveProbe.this).callSerially(new Runnable() {
                        public void run() {
                            if (attempt != generation) { return; }
                            busy = false; active = null; failedSend(); updateHeader("Fehler; Verlauf aktualisieren");
                            javax.microedition.lcdui.Alert alert = new Alert("Verbindung", error.toString(), null, AlertType.ERROR);
                            alert.setTimeout(Alert.FOREVER); Display.getDisplay(CurveProbe.this).setCurrent(alert, chat); saveState();
                        }
                    });
                } finally { close(connection); }
            }
        }).start();
        new Thread(new Runnable() {
            public void run() {
                try { Thread.sleep(180000); } catch (InterruptedException ignored) { return; }
                synchronized (CurveProbe.this) {
                    if (attempt != generation || !busy) { return; }
                    close(active); active = null; busy = false; final int expired = ++generation;
                    Display.getDisplay(CurveProbe.this).callSerially(new Runnable() {
                        public void run() { if (generation == expired) { failedSend(); updateHeader("Zeitlimit; Verlauf prüfen"); saveState(); } }
                    });
                }
            }
        }).start();
    }
    private void failedSend() {
        if (submitted.length() > 0) {
            if (input.getString().length() == 0) { input.setString(submitted); quoteId = submittedQuoteId; quoteText = submittedQuote; }
            else { storeDraft(threadId + "/ungesendet", submitted, submittedQuoteId, submittedQuote); }
            view.removePending(); submitted = "";
        }
    }
    private static final class ChatPacket {
        String id, title, cwd, cursor; boolean writable; ChatMessage[] messages;
    }
    private ChatPacket readPacket(DataInputStream stream) throws IOException {
        ChatPacket packet = new ChatPacket(); packet.id = readFrame(stream, 256); packet.title = readFrame(stream, 512); packet.cwd = readFrame(stream, 4096);
        packet.writable = stream.readUnsignedByte() == 1; packet.cursor = readFrame(stream, 256);
        int count = stream.readInt(); if (count < 0 || count > 20) { throw new IOException("Ungueltige Nachrichtenzahl"); }
        packet.messages = new ChatMessage[count]; int budget = 0;
        for (int i = 0; i < count; i++) {
            String id = readFrame(stream, 256); boolean user = stream.readUnsignedByte() == 1;
            String body = readFrame(stream, 22000); String cited = readFrame(stream, 1024); boolean cut = stream.readUnsignedByte() == 1;
            budget += id.getBytes("UTF-8").length + body.getBytes("UTF-8").length + cited.getBytes("UTF-8").length;
            if (budget > 22000) { throw new IOException("Verlauf zu groß"); }
            packet.messages[i] = new ChatMessage(id, user, body, cited, cut);
        }
        return packet;
    }
    private void apply(ChatPacket packet, String operation, boolean olderPage) {
        boolean changed = !threadId.equals(packet.id);
        if (changed) {
            String nextDraft = input.getString(), nextQuoteId = quoteId, nextQuote = quoteText;
            rememberDraft(); threadId = packet.id; view.clear(); restoreDraft(); historyCursor = ""; loadedOlder = false;
            if (operation.equals("SEND")) { input.setString(nextDraft); quoteId = nextQuoteId; quoteText = nextQuote; }
        }
        if (operation.equals("SEND")) { view.removePending(); submitted = ""; }
        title = packet.title; cwd = packet.cwd; writableChat = packet.writable;
        if (olderPage) { loadedOlder = true; }
        if (changed || olderPage || !loadedOlder) { historyCursor = packet.cursor; }
        view.merge(packet.messages, olderPage); updateHeader("Bereit");
        input.setConstraints(writableChat ? TextField.ANY : TextField.ANY | TextField.UNEDITABLE);
        Display.getDisplay(this).setCurrent(chat); saveState();
    }
    private static String[] split(String value, char delimiter) {
        Vector parts = new Vector(); int start = 0;
        for (int i = 0; i < value.length(); i++) { if (value.charAt(i) == delimiter) { parts.addElement(value.substring(start, i)); start = i + 1; } }
        parts.addElement(value.substring(start)); String[] result = new String[parts.size()]; parts.copyInto(result); return result;
    }
    private void showList(String operation, String reply) {
        projectSelection = operation.equals("PROJECTS"); selection = new List(projectSelection ? "Projekt für neuen Chat" : "Chats", List.IMPLICIT);
        String[] rows = split(reply, '\n'); selectedIds = new String[rows.length]; selectedFolders = new String[rows.length]; selectedTitles = new String[rows.length]; nextCursor = ""; int count = 0;
        for (int i = 0; i < rows.length; i++) {
            if (projectSelection) {
                if (rows[i].length() == 0) { continue; }
                selectedFolders[count] = rows[i]; selectedIds[count] = ""; selectedTitles[count] = rows[i]; selection.append(rows[i], null); count++;
            } else {
                String[] fields = split(rows[i], '\t');
                if (fields[0].equals("@next")) { nextCursor = fields.length > 1 ? fields[1] : ""; continue; }
                if (fields.length < 4) { continue; }
                selectedIds[count] = fields[0]; selectedFolders[count] = fields[2]; selectedTitles[count] = fields[1];
                selection.append((fields[3].equals("Curve") ? "" : "[Lesen] ") + fields[1], null); count++;
            }
        }
        if (count == 0) { updateHeader("Keine Chats gefunden"); return; }
        selection.addCommand(back); if (nextCursor.length() > 0) { selection.addCommand(more); } selection.setCommandListener(this); Display.getDisplay(this).setCurrent(selection);
    }
    private void probe() {
        final String address = host.getString().trim(); connectionStatus.setText("Verbinde ...");
        new Thread(new Runnable() {
            public void run() {
                StreamConnection connection = null; String result;
                try {
                    connection = (StreamConnection) Connector.open("socket://" + address + ":8766;deviceside=true;interface=wifi", Connector.READ_WRITE, true);
                    OutputStream out = connection.openOutputStream(); out.write("PING CurveProbe\n".getBytes("UTF-8")); out.flush();
                    InputStream in = connection.openInputStream(); StringBuffer response = new StringBuffer(); int ch;
                    while ((ch = in.read()) != -1 && ch != '\n' && response.length() < 256) { response.append((char) ch); }
                    result = response.toString().equals("PONG CurveProbe Mac") ? "Mac im WLAN erreichbar" : "Unerwartete Antwort";
                } catch (Exception error) { result = error.toString(); } finally { close(connection); }
                final String value = result; Display.getDisplay(CurveProbe.this).callSerially(new Runnable() { public void run() { connectionStatus.setText(value); } });
            }
        }).start();
    }
    private void rememberDraft() { storeDraft(threadId, input.getString(), quoteId, quoteText); }
    private void storeDraft(String id, String text, String citedId, String cited) {
        int index = draftIds.indexOf(id);
        if (index < 0) { draftIds.addElement(id); drafts.addElement(text); quoteIds.addElement(citedId); quotes.addElement(cited); }
        else { drafts.setElementAt(text, index); quoteIds.setElementAt(citedId, index); quotes.setElementAt(cited, index); }
    }
    private void restoreDraft() {
        int index = draftIds.indexOf(threadId); input.setString(index < 0 ? "" : (String) drafts.elementAt(index));
        quoteId = index < 0 ? "" : (String) quoteIds.elementAt(index); quoteText = index < 0 ? "" : (String) quotes.elementAt(index);
    }
    private void loadState() {
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore("CurveRemote", true);
            if (store.getNumRecords() > 0) {
                DataInputStream data = new DataInputStream(new ByteArrayInputStream(store.getRecord(1)));
                threadId = data.readUTF(); cwd = data.readUTF(); writableChat = data.readBoolean();
                if (data.available() > 0) {
                    title = data.readUTF(); host.setString(data.readUTF()); int count = data.readInt(); if (count < 0 || count > 100) { throw new IOException("Draft count"); }
                    for (int i = 0; i < count; i++) { storeDraft(data.readUTF(), data.readUTF(), data.readUTF(), data.readUTF()); } restoreDraft();
                }
            }
        } catch (Exception ignored) { }
        finally { if (store != null) { try { store.closeRecordStore(); } catch (Exception ignored) { } } }
    }
    private void saveState() {
        rememberDraft(); RecordStore store = null;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream data = new DataOutputStream(bytes);
            data.writeUTF(threadId); data.writeUTF(cwd); data.writeBoolean(writableChat); data.writeUTF(title); data.writeUTF(host.getString());
            int begin = Math.max(0, draftIds.size() - 100); data.writeInt(draftIds.size() - begin);
            for (int i = begin; i < draftIds.size(); i++) { data.writeUTF((String) draftIds.elementAt(i)); data.writeUTF((String) drafts.elementAt(i)); data.writeUTF((String) quoteIds.elementAt(i)); data.writeUTF((String) quotes.elementAt(i)); }
            data.flush(); byte[] value = bytes.toByteArray(); store = RecordStore.openRecordStore("CurveRemote", true);
            if (store.getNumRecords() == 0) { store.addRecord(value, 0, value.length); } else { store.setRecord(1, value, 0, value.length); }
        } catch (Exception ignored) { view.status("Entwurf nicht gespeichert"); }
        finally { if (store != null) { try { store.closeRecordStore(); } catch (Exception ignored) { } } }
    }
    private static String readFrame(DataInputStream stream, int limit) throws IOException {
        int length = stream.readInt(); if (length < 0 || length > limit) { throw new IOException("Antwort zu groß"); }
        byte[] data = new byte[length]; stream.readFully(data); return new String(data, "UTF-8");
    }
    private static void writeFrame(DataOutputStream stream, String text) throws IOException { byte[] data = text.getBytes("UTF-8"); stream.writeInt(data.length); stream.write(data); }
}
