// SPDX-License-Identifier: Apache-2.0
package javax.microedition.io;
public interface StreamConnection extends Connection { java.io.InputStream openInputStream() throws java.io.IOException; java.io.OutputStream openOutputStream() throws java.io.IOException; }
