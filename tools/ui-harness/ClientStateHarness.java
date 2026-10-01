// SPDX-License-Identifier: Apache-2.0
import java.lang.reflect.*;
import java.io.*;
import javax.microedition.lcdui.*;
import javax.microedition.rms.RecordStore;

public final class ClientStateHarness {
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 private static Field field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f;}
 private static Object get(Object o,String name)throws Exception{return field(o,name).get(o);}
 private static void set(Object o,String name,Object value)throws Exception{field(o,name).set(o,value);}
 private static void call(Object o,String name)throws Exception{Method m=o.getClass().getDeclaredMethod(name,new Class[0]);m.setAccessible(true);m.invoke(o,new Object[0]);}
 private static Object packet(String id,ChatMessage[] messages)throws Exception{
  Class type=Class.forName("CurveProbe$ChatPacket");Constructor ctor=type.getDeclaredConstructor(new Class[0]);ctor.setAccessible(true);Object packet=ctor.newInstance(new Object[0]);
  set(packet,"id",id);set(packet,"title","Chat "+id);set(packet,"cwd","/tmp/project");set(packet,"cursor","");set(packet,"writable",Boolean.TRUE);set(packet,"messages",messages);return packet;
 }
 private static void apply(CurveProbe app,Object packet,String operation)throws Exception{
  Method method=CurveProbe.class.getDeclaredMethod("apply",new Class[]{packet.getClass(),String.class,Boolean.TYPE});method.setAccessible(true);method.invoke(app,new Object[]{packet,operation,Boolean.FALSE});
 }
 public static void main(String[] args)throws Exception{
  if(args.length>0){
   CurveProbe decoder=new CurveProbe();Method read=CurveProbe.class.getDeclaredMethod("readPacket",new Class[]{DataInputStream.class});read.setAccessible(true);
   Object decoded=read.invoke(decoder,new Object[]{new DataInputStream(new FileInputStream(args[0]))});
   check(get(decoded,"id").equals("fixture-chat"),"Python/Java thread frame");check(get(decoded,"title").equals("Grüße äöü"),"Unicode title frame");
   ChatMessage[] records=(ChatMessage[])get(decoded,"messages");check(records.length==2,"message count");
   check(!records[0].user&&records[0].text.indexOf("Du:")>=0,"structured roles are not inferred from text");
   check(records[1].user&&records[1].quote.equals("Zitat äöü")&&records[1].truncated,"role quote truncation flags");
   ByteArrayOutputStream malformed=new ByteArrayOutputStream();DataOutputStream invalid=new DataOutputStream(malformed);invalid.writeInt(999999);
   boolean rejected=false;try{read.invoke(decoder,new Object[]{new DataInputStream(new ByteArrayInputStream(malformed.toByteArray()))});}catch(InvocationTargetException error){rejected=error.getCause() instanceof IOException;}
   check(rejected,"oversized frame rejected before allocation");
   System.out.println("PASS: Python encoder to actual Java decoder, UTF-8, message identity, quote flags and malformed-frame rejection");
  }
  RecordStore.reset();CurveProbe app=new CurveProbe();TextField input=(TextField)get(app,"input");
  check(input.getString().equals(""),"no canned prompt");
  apply(app,packet("t1",new ChatMessage[0]),"HISTORY");input.setString("Entwurf eins äöü");set(app,"quoteId","a1");set(app,"quoteText","Zitat eins");
  apply(app,packet("t2",new ChatMessage[0]),"HISTORY");check(input.getString().equals(""),"new chat empty draft");input.setString("Entwurf zwei");
  apply(app,packet("t1",new ChatMessage[0]),"HISTORY");check(input.getString().equals("Entwurf eins äöü"),"chat switch restores draft");check(get(app,"quoteId").equals("a1"),"quote restored");
  call(app,"saveState");CurveProbe restored=new CurveProbe();check(((TextField)get(restored,"input")).getString().equals("Entwurf eins äöü"),"restart draft");check(get(restored,"quoteId").equals("a1"),"restart quote");
  input.setString("Neu geschrieben während Codex arbeitet");set(app,"submitted","Abgesendete erste Frage");
  apply(app,packet("new-id",new ChatMessage[]{new ChatMessage("a",false,"Antwort","",false)}),"SEND");check(input.getString().startsWith("Neu geschrieben"),"first SEND retains next draft");
  set(app,"submitted","Unklar gesendete Frage");set(app,"submittedQuoteId","a2");set(app,"submittedQuote","Zitat zwei");call(app,"failedSend");
  check(input.getString().startsWith("Neu geschrieben"),"failure preserves next draft");
  app.commandAction((Command)get(app,"recover"),(Displayable)get(app,"chat"));check(input.getString().equals("Unklar gesendete Frage"),"failed submitted text recoverable");check(get(app,"quoteId").equals("a2"),"failed quote recoverable");
  app.commandAction((Command)get(app,"recover"),(Displayable)get(app,"chat"));check(input.getString().startsWith("Neu geschrieben"),"recovery swap preserves both drafts");
  input.setString("");set(app,"submitted","Frage ohne neuen Entwurf");call(app,"failedSend");check(input.getString().equals("Frage ohne neuen Entwurf"),"failed send restores original draft");
  Object refreshPacket=packet("new-id",new ChatMessage[]{new ChatMessage("a",false,"Antwort","",false)});set(refreshPacket,"cursor","older-anchor");
  apply(app,refreshPacket,"HISTORY");check(get(app,"historyCursor").equals("older-anchor"),"refresh updates older cursor after initial short history");
  set(app,"loadedOlder",Boolean.TRUE);set(app,"historyCursor","earlier-anchor");
  apply(app,refreshPacket,"HISTORY");check(get(app,"historyCursor").equals("earlier-anchor"),"refresh keeps already loaded older-page cursor");
  call(app,"showHistory");
  List historyList=(List)get(app,"messageHistory");
  check(Display.getDisplay(app).current==historyList,"history opens a visible screen instead of silently refreshing");
  check(historyList.entries.size()==3&&((String)historyList.entries.elementAt(0)).startsWith("Codex:"),"history has message, older and refresh entries");
  app.commandAction(List.SELECT_COMMAND,historyList);
  check(Display.getDisplay(app).current==get(app,"chat"),"history selection returns to chat");
  check(get(get(app,"view"),"selected").equals(Integer.valueOf(0)),"history selection focuses selected message");
  set(app,"busy",Boolean.TRUE);set(app,"generation",Integer.valueOf(17));
  call(app,"pauseApp");check(get(app,"busy").equals(Boolean.TRUE)&&get(app,"generation").equals(Integer.valueOf(17)),"permission-dialog pause preserves pending connection instead of silently cancelling it");
  set(app,"busy",Boolean.FALSE);
  app.commandAction((Command)get(app,"back"),historyList);
  check(get(get(app,"chat"),"zone").equals(Integer.valueOf(2)),"back restores composer focus");
  // Existing 0.3 record migrates without losing the selected thread/folder.
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeUTF("old-thread");out.writeUTF("/old/folder");out.writeBoolean(true);out.flush();RecordStore.legacy(bytes.toByteArray());
  CurveProbe migrated=new CurveProbe();check(get(migrated,"threadId").equals("old-thread"),"0.3 selected chat migration");check(get(migrated,"cwd").equals("/old/folder"),"0.3 folder migration");
  System.out.println("PASS: actual CurveProbe draft switching, quote persistence, first-send reconciliation, recoverable failures and 0.3 state migration");
 }
}
