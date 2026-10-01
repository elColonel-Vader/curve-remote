// SPDX-License-Identifier: Apache-2.0
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.*;

/** Full fixed-screen rendering and physical-key navigation tests. */
public final class ChatScreenHarness {
 private static int fontSize(){return Integer.getInteger("midp.fontSize",14).intValue();}
 private static int sent, chats, history, older, projects, edits, newChats;
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  ChatView view=new ChatView(new ChatView.Actions(){public void chats(){}public void newChat(){}public void reply(ChatMessage m){}});
  TextField text=new TextField(null,"",2048,TextField.ANY);
  ChatScreen screen=new ChatScreen(view,text,new ChatScreen.Actions(){
   public void send(){sent++;}public void chats(){chats++;}public void history(){history++;}public void older(){older++;}public void projects(){projects++;}
   public void newChat(){newChats++;}public void edit(){edits++;}public void changed(){}public boolean writable(){return true;}public boolean hasOlder(){return true;}
  });
  screen.sizeChanged(480,screen.getHeight());
  view.header("PDF-Export beschleunigen","/tmp/angebot","Bereit");
  view.merge(new ChatMessage[]{new ChatMessage("u",true,"Wo hängt der Export?","",false),new ChatMessage("a",false,"Die Bilder werden mehrfach geladen. Möchtest du die Details sehen?","",false)},false);
  // Keyboard numeric keys stay text, including values often mistaken for game actions.
  String value="Grüße äöü ß 2468";for(int i=0;i<value.length();i++)screen.keyPressed(value.charAt(i));
  check(text.getString().equals(value),"direct Unicode/number typing");screen.keyPressed(8);check(text.getString().equals(value.substring(0,value.length()-1)),"backspace");
  screen.keyPressed(13);check(sent==1,"Enter sends without menu");
  screen.focusInput();screen.keyPressed(-4);screen.keyPressed(-5);check(sent==2,"visible send button via trackpad");
  screen.focusInput();screen.keyPressed(-1);screen.keyPressed(-5);check(chats==1,"visible chats action");
  screen.keyPressed(-4);screen.keyPressed(13);check(history==1&&sent==2,"Enter activates history when navigation focused");
  screen.keyPressed(-4);screen.keyPressed(-5);check(older==1,"visible older messages action");
  screen.keyPressed(-4);screen.keyPressed(-5);check(projects==1,"visible project action");
  screen.keyPressed(-4);screen.keyPressed(-5);check(newChats==1,"new chat accessible from navigation without scrolling transcript");
  screen.focusInput();screen.keyPressed(-5);check(edits==1,"native editor available for complex keyboard input");
  BufferedImage image=new BufferedImage(480,screen.getHeight(),BufferedImage.TYPE_INT_RGB);screen.paint(new Graphics(image.createGraphics()));ImageIO.write(image,"png",new File(args[0]+"/chat-screen-v05-"+fontSize()+".png"));
  boolean headerStatus=false;for(int y=0;y<view.headerHeight();y++)for(int x=0;x<480;x++)if((image.getRGB(x,y)&0xffffff)==0x85c9a4)headerStatus=true;
  check(headerStatus,"green ready indicator is inside header");
  check((image.getRGB(20,screen.getHeight()-15)&0xffffff)==0x26282d,"composer stays charcoal");
  int fontHeight=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL).getHeight();
  int navTop=screen.getHeight()-(fontHeight+24)-(fontHeight+12);
  check((image.getRGB(10,navTop+5)&0xffffff)==0x222429,"navigation background is actually painted outside transcript clip");
  BufferedImage whiteInitial=new BufferedImage(480,screen.getHeight(),BufferedImage.TYPE_INT_RGB);
  java.awt.Graphics2D initialGraphics=whiteInitial.createGraphics();initialGraphics.setColor(java.awt.Color.WHITE);initialGraphics.fillRect(0,0,480,screen.getHeight());screen.paint(new Graphics(initialGraphics));
  for(int py=0;py<screen.getHeight();py++)for(int px=0;px<480;px++)check(image.getRGB(px,py)==whiteInitial.getRGB(px,py),"full Canvas repaint must not retain white native background pixels at "+px+","+py);
  int composerY=screen.getHeight()-(fontHeight+24)+3;
  int borderBefore=image.getRGB(40,composerY)&0xffffff;
  screen.keyPressed(-1);BufferedImage navigating=new BufferedImage(480,screen.getHeight(),BufferedImage.TYPE_INT_RGB);screen.paint(new Graphics(navigating.createGraphics()));
  check(borderBefore==0x3a3d44&&(navigating.getRGB(40,composerY)&0xffffff)==borderBefore,"composer outline stays subtle and identical after focus moves to navigation");
  System.out.println("PASS: full dark screen, compact composer, direct Enter/trackpad send, all visible navigation actions, Unicode and digits");
 }
}
