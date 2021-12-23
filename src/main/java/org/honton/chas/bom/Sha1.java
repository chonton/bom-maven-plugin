package org.honton.chas.bom;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Calculate digest for a file. */
public class Sha1 {
  private static final char[] HEXITS = "0123456789abcdef".toCharArray();
  private static final int BUFFER_SIZE = 0x10000;

  private final MessageDigest digest;
  private final ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);

  public Sha1() throws NoSuchAlgorithmException {
    digest = MessageDigest.getInstance("SHA-1");
  }

  public static String encode(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte aByte : bytes) {
      int twoHexes = aByte & 0xff;
      sb.append(HEXITS[twoHexes >>> 4]);
      sb.append(HEXITS[twoHexes & 0x0f]);
    }
    return sb.toString();
  }

  public byte[] getChecksumBytes(Path path) throws IOException {
    try (InputStream is = Files.newInputStream(path)) {
      digest.reset();
      readStream(is);
      return digest.digest();
    }
  }

  public String getChecksum(Path path) throws IOException {
    return encode(getChecksumBytes(path));
  }

  private void readStream(InputStream is) throws IOException {
    try (ReadableByteChannel byteChannel = Channels.newChannel(is)) {
      for (; ; ) {
        int bytes = byteChannel.read(byteBuffer);
        if (bytes < 0) {
          break;
        }
        byteBuffer.flip();
        digest.update(byteBuffer);
        byteBuffer.clear();
      }
    }
  }
}
