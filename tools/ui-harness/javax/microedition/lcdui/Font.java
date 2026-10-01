// SPDX-License-Identifier: Apache-2.0
package javax.microedition.lcdui;
import java.awt.image.BufferedImage;
public final class Font {
    public static final int FACE_SYSTEM=0, STYLE_PLAIN=0, STYLE_BOLD=1, SIZE_SMALL=0;
    final java.awt.Font awt;
    private final java.awt.FontMetrics metrics;
    private Font(int style) {
        awt=new java.awt.Font("SansSerif",style==STYLE_BOLD?java.awt.Font.BOLD:java.awt.Font.PLAIN,Integer.getInteger("midp.fontSize",14).intValue());
        metrics=new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB).createGraphics().getFontMetrics(awt);
    }
    public static Font getFont(int face,int style,int size) { return new Font(style); }
    public int getHeight() { return metrics.getHeight(); }
    public int stringWidth(String text) { return metrics.stringWidth(text); }
    public int substringWidth(String text,int offset,int length) { return stringWidth(text.substring(offset,offset+length)); }
}
