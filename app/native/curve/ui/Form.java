// SPDX-License-Identifier: Apache-2.0
package curve.ui;
public final class Form extends Displayable {
 public Form(String title){super(title);}
 public void append(Item item){if(item instanceof TextField){screen.add(((TextField)item).nativeField());}else if(item instanceof StringItem){screen.add(((StringItem)item).nativeField());}}
}
