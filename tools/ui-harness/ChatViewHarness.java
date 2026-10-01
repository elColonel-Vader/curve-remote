// SPDX-License-Identifier: Apache-2.0
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;

/** Exercises the actual handset renderer with an AWT-backed MIDP graphics adapter. */
public final class ChatViewHarness {
    private static int chats, news; private static String replied;
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    private static int integer(ChatView view,String name)throws Exception { Field field=ChatView.class.getDeclaredField(name);field.setAccessible(true);return field.getInt(view); }
    private static void render(ChatView view,String path)throws Exception {
        BufferedImage image=new BufferedImage(480,246,BufferedImage.TYPE_INT_RGB);
        view.paint(new Graphics(image.createGraphics()),480,246);ImageIO.write(image,"png",new File(path));
    }
    public static void main(String[] args)throws Exception {
        new File(args[0]).mkdirs();
        ChatView view=new ChatView(new ChatView.Actions(){
            public void chats(){chats++;} public void newChat(){news++;} public void reply(ChatMessage m){replied=m.id;}
        });
        view.sizeChanged(480,246); view.header("BlackBerry Curve","/tmp/curve-remote","Bereit");
        ChatMessage user=new ChatMessage("u1",true,"Kannst du mir beim Chatfenster helfen?","",false);
        ChatMessage answer=new ChatMessage("a1",false,"Ja. Du kannst direkt weiterschreiben und meine Nachrichten zum Zitieren auswählen.","",false);
        view.merge(new ChatMessage[]{user,answer},false); check(view.count()==2,"initial history");
        render(view,args[0]+"/chat-normal.png");
        view.merge(new ChatMessage[]{user,answer},false); check(view.count()==2,"refresh duplicates");
        int[] rectangle=new int[4]; view.traverse(0,480,246,rectangle);view.activate();check(chats==1,"header switcher");
        view.traverse(Canvas.DOWN,480,246,rectangle);view.activate();check(news==1,"new button");
        view.traverse(Canvas.DOWN,480,246,rectangle);view.traverse(Canvas.DOWN,480,246,rectangle);view.activate();check("a1".equals(replied),"stable quote selection");
        view.quote("Du kannst direkt weiterschreiben ...");render(view,args[0]+"/chat-quote.png");
        StringBuffer longText=new StringBuffer();for(int i=0;i<45;i++)longText.append("Lange Antworten bleiben lesbar und lassen sich mit dem Trackpad scrollen.\n");
        view.merge(new ChatMessage[]{new ChatMessage("a2",false,longText.toString(),"",false)},false);
        // Read from the top, then verify new arrivals do not force bottom scrolling.
        for(int i=0;i<100;i++)view.traverse(Canvas.UP,480,246,rectangle);
        int before=integer(view,"scroll");check(!view.atBottom(),"reader is above bottom");
        view.merge(new ChatMessage[]{new ChatMessage("a3",false,"Neue Nachricht","",false)},false);
        check(integer(view,"scroll")==before,"new arrival preserves reader position");
        before=integer(view,"scroll");
        view.merge(new ChatMessage[]{new ChatMessage("old",true,"Ältere Nachricht","",false)},true);
        check(integer(view,"scroll")>before,"older page preserves viewport content");
        view.pending("Meine nächste Frage","Zitat");check(view.message(view.count()-1).pending,"pending shown");view.removePending();
        for(int i=0;i<view.count();i++)check(!view.message(i).pending,"pending removed on reconciliation");
        // Width adaptation and long unbroken strings must always terminate.
        view.sizeChanged(240,246);view.merge(new ChatMessage[]{new ChatMessage("token",false,"abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz","",false)},false);
        BufferedImage small=new BufferedImage(240,246,BufferedImage.TYPE_INT_RGB);view.paint(new Graphics(small.createGraphics()),240,246);
        ImageIO.write(small,"png",new File(args[0]+"/chat-narrow.png"));
        System.out.println("PASS: actual ChatView layout, ID reconciliation, quote action, focus, long-text navigation, scroll preservation, older pages and pending messages");
    }
}
