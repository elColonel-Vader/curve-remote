// SPDX-License-Identifier: Apache-2.0
package curve.ui;
public final class Command {
 public static final int SCREEN=1, BACK=2, OK=4, EXIT=7, ITEM=8;
 public final String label; public final int type, priority;
 public Command(String label,int type,int priority){this.label=label;this.type=type;this.priority=priority;}
}
