// SPDX-License-Identifier: Apache-2.0
/** One stable conversation item; independent of its rendered text. */
public final class ChatMessage {
    public String id, text, quote;
    public boolean user, truncated, pending;
    public ChatMessage(String id, boolean user, String text, String quote, boolean truncated) {
        this.id = id; this.user = user; this.text = text; this.quote = quote; this.truncated = truncated;
    }
}
