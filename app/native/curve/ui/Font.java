// SPDX-License-Identifier: Apache-2.0
package curve.ui;
public final class Font {
 public static final int FACE_SYSTEM=0,STYLE_PLAIN=0,STYLE_BOLD=1,SIZE_SMALL=8;
 final net.rim.device.api.ui.Font nativeFont;
 private Font(int style){nativeFont=net.rim.device.api.ui.Font.getDefault().derive(style==STYLE_BOLD?net.rim.device.api.ui.Font.BOLD:net.rim.device.api.ui.Font.PLAIN,20);}
 public static Font getFont(int face,int style,int size){return new Font(style);}
 public int getHeight(){return nativeFont.getHeight();}
 public int stringWidth(String text){return nativeFont.getAdvance(text);}
 public int substringWidth(String text,int start,int count){return stringWidth(text.substring(start,start+count));}
}
