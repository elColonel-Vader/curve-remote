// SPDX-License-Identifier: Apache-2.0
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
/** Original Curve Remote launcher artwork; no vendor logos or external assets. */
public final class CreateIcon {
 public static void main(String[] args) throws Exception {
  for (int size : new int[]{64,128}) {
   BufferedImage image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
   Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.scale(size/64.0,size/64.0);
   g.setColor(new Color(0x131417));g.fill(new RoundRectangle2D.Float(2,2,60,60,14,14));
   g.setColor(new Color(0x82b5ed));g.fill(new RoundRectangle2D.Float(10,12,44,34,8,8));
   Path2D tail=new Path2D.Float();tail.moveTo(18,43);tail.lineTo(18,53);tail.lineTo(30,43);tail.closePath();g.fill(tail);
   g.setColor(new Color(0x131417));g.setStroke(new BasicStroke(3,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
   g.drawLine(19,23,25,29);g.drawLine(25,29,19,35);g.drawLine(31,35,42,35);
   g.setColor(new Color(0x85c9a4));g.fillOval(46,46,12,12);g.dispose();
   ImageIO.write(image,"png",new File(args[0]+"/icon"+(size==64?"":"-128")+".png"));
  }
 }
}
