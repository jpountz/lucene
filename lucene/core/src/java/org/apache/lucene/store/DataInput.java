/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.lucene.util.BitUtil;

/**
 * Abstract base class for performing read operations of Lucene's low-level data types.
 *
 * <p>{@code DataInput} may only be used from one thread, because it is not thread safe (it keeps
 * internal state like file position). To allow multithreaded use, every {@code DataInput} instance
 * must be cloned before used in another thread. Subclasses must therefore implement {@link
 * #clone()}, returning a new {@code DataInput} which operates on the same underlying resource, but
 * positioned independently.
 */
public abstract class DataInput implements Cloneable {
  // the maximum length of a single group-varint is 4 integers + 1 byte flag.
  static final int MAX_LENGTH_PER_GROUP = 17;
  static final int[] GROUP_VINT_MASKS = new int[] {0xFF, 0xFFFF, 0xFFFFFF, 0xFFFFFFFF};

  /**
   * Reads and returns a single byte.
   *
   * @see DataOutput#writeByte(byte)
   */
  public abstract byte readByte() throws IOException;

  /**
   * Reads a specified number of bytes into an array at the specified offset.
   *
   * @param b the array to read bytes into
   * @param offset the offset in the array to start storing bytes
   * @param len the number of bytes to read
   * @see DataOutput#writeBytes(byte[],int)
   */
  public abstract void readBytes(byte[] b, int offset, int len) throws IOException;

  /**
   * Reads a specified number of bytes into an array at the specified offset with control over
   * whether the read should be buffered (callers who have their own buffer should pass in "false"
   * for useBuffer). Currently only {@link BufferedIndexInput} respects this parameter.
   *
   * @param b the array to read bytes into
   * @param offset the offset in the array to start storing bytes
   * @param len the number of bytes to read
   * @param useBuffer set to false if the caller will handle buffering.
   * @see DataOutput#writeBytes(byte[],int)
   */
  public void readBytes(byte[] b, int offset, int len, boolean useBuffer) throws IOException {
    // Default to ignoring useBuffer entirely
    readBytes(b, offset, len);
  }

  /**
   * Reads two bytes and returns a short (LE byte order).
   *
   * @see DataOutput#writeShort(short)
   * @see BitUtil#VH_LE_SHORT
   */
  public short readShort() throws IOException {
    final byte b1 = readByte();
    final byte b2 = readByte();
    return (short) (((b2 & 0xFF) << 8) | (b1 & 0xFF));
  }

  /**
   * Reads four bytes and returns an int (LE byte order).
   *
   * @see DataOutput#writeInt(int)
   * @see BitUtil#VH_LE_INT
   */
  public int readInt() throws IOException {
    final byte b1 = readByte();
    final byte b2 = readByte();
    final byte b3 = readByte();
    final byte b4 = readByte();
    return ((b4 & 0xFF) << 24) | ((b3 & 0xFF) << 16) | ((b2 & 0xFF) << 8) | (b1 & 0xFF);
  }

  // just for benchmark only, it will be removed before the PR is merged
  public void readGroupVIntsBaseline(long[] docs, int limit) throws IOException {
    int i;
    for (i = 0; i <= limit - 4; i += 4) {
      fallbackReadGroupVInt(docs, i);
    }
    for (; i < limit; ++i) {
      docs[i] = readVInt();
    }
  }

  /**
   * Read all the group varints, including the tail vints.
   *
   * @param docs the array to read ints into.
   * @param limit the number of int values to read.
   */
  public void readGroupVInts(long[] docs, int limit) throws IOException {
    int i;
    for (i = 0; i <= limit - 4; i += 4) {
      fallbackReadGroupVInt(docs, i);
    }
    for (; i < limit; ++i) {
      docs[i] = readVInt();
    }
  }

  /**
   * Read single group varint. we need a long[] because this is what postings are using.
   *
   * @param docs the array to read ints into.
   * @param offset the offset in the array to start storing ints.
   */
  protected void fallbackReadGroupVInt(long[] docs, int offset) throws IOException {
    final int flag = readByte() & 0xFF;

    final int n1Minus1 = flag >> 6;
    final int n2Minus1 = (flag >> 4) & 0x03;
    final int n3Minus1 = (flag >> 2) & 0x03;
    final int n4Minus1 = flag & 0x03;

    docs[offset] = readLongInGroup(n1Minus1);
    docs[offset + 1] = readLongInGroup(n2Minus1);
    docs[offset + 2] = readLongInGroup(n3Minus1);
    docs[offset + 3] = readLongInGroup(n4Minus1);
  }

  private long readLongInGroup(int numBytesMinus1) throws IOException {
    switch (numBytesMinus1) {
      case 0:
        return readByte() & 0xFFL;
      case 1:
        return readShort() & 0xFFFFL;
      case 2:
        return (readShort() & 0xFFFFL) | ((readByte() & 0xFFL) << 16);
      default:
        return readInt() & 0xFFFFFFFFL;
    }
  }

  /**
   * Reads an int stored in variable-length format. Reads between one and five bytes. Smaller values
   * take fewer bytes. Negative numbers are supported, but should be avoided.
   *
   * <p>The format is described further in {@link DataOutput#writeVInt(int)}.
   *
   * @see DataOutput#writeVInt(int)
   */
  public int readVInt() throws IOException {
    byte b = readByte();
    int i = b & 0x7F;
    for (int shift = 7; (b & 0x80) != 0; shift += 7) {
      b = readByte();
      i |= (b & 0x7F) << shift;
    }
    return i;
  }

  /**
   * Read a {@link BitUtil#zigZagDecode(int) zig-zag}-encoded {@link #readVInt() variable-length}
   * integer.
   *
   * @see DataOutput#writeZInt(int)
   */
  public int readZInt() throws IOException {
    return BitUtil.zigZagDecode(readVInt());
  }

  /**
   * Reads eight bytes and returns a long (LE byte order).
   *
   * @see DataOutput#writeLong(long)
   * @see BitUtil#VH_LE_LONG
   */
  public long readLong() throws IOException {
    return (readInt() & 0xFFFFFFFFL) | (((long) readInt()) << 32);
  }

  /**
   * Read a specified number of longs.
   *
   * @lucene.experimental
   */
  public void readLongs(long[] dst, int offset, int length) throws IOException {
    Objects.checkFromIndexSize(offset, length, dst.length);
    for (int i = 0; i < length; ++i) {
      dst[offset + i] = readLong();
    }
  }

  /**
   * Reads a specified number of ints into an array at the specified offset.
   *
   * @param dst the array to read bytes into
   * @param offset the offset in the array to start storing ints
   * @param length the number of ints to read
   */
  public void readInts(int[] dst, int offset, int length) throws IOException {
    Objects.checkFromIndexSize(offset, length, dst.length);
    for (int i = 0; i < length; ++i) {
      dst[offset + i] = readInt();
    }
  }

  /**
   * Reads a specified number of floats into an array at the specified offset.
   *
   * @param floats the array to read bytes into
   * @param offset the offset in the array to start storing floats
   * @param len the number of floats to read
   */
  public void readFloats(float[] floats, int offset, int len) throws IOException {
    Objects.checkFromIndexSize(offset, len, floats.length);
    for (int i = 0; i < len; i++) {
      floats[offset + i] = Float.intBitsToFloat(readInt());
    }
  }

  /**
   * Reads a long stored in variable-length format. Reads between one and nine bytes. Smaller values
   * take fewer bytes. Negative numbers are not supported.
   *
   * <p>The format is described further in {@link DataOutput#writeVInt(int)}.
   *
   * @see DataOutput#writeVLong(long)
   */
  public long readVLong() throws IOException {
    byte b = readByte();
    long i = b & 0x7F;
    for (int shift = 7; (b & 0x80) != 0; shift += 7) {
      b = readByte();
      i |= (b & 0x7FL) << shift;
    }
    return i;
  }

  /**
   * Read a {@link BitUtil#zigZagDecode(long) zig-zag}-encoded {@link #readVLong() variable-length}
   * integer. Reads between one and ten bytes.
   *
   * @see DataOutput#writeZLong(long)
   */
  public long readZLong() throws IOException {
    return BitUtil.zigZagDecode(readVLong());
  }

  /**
   * Reads a string.
   *
   * @see DataOutput#writeString(String)
   */
  public String readString() throws IOException {
    int length = readVInt();
    final byte[] bytes = new byte[length];
    readBytes(bytes, 0, length);
    return new String(bytes, 0, length, StandardCharsets.UTF_8);
  }

  /**
   * Returns a clone of this stream.
   *
   * <p>Clones of a stream access the same data, and are positioned at the same point as the stream
   * they were cloned from.
   *
   * <p>Expert: Subclasses must ensure that clones may be positioned at different points in the
   * input from each other and from the stream they were cloned from.
   */
  @Override
  public DataInput clone() {
    try {
      return (DataInput) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new Error("This cannot happen: Failing to clone DataInput", e);
    }
  }

  /**
   * Reads a Map&lt;String,String&gt; previously written with {@link
   * DataOutput#writeMapOfStrings(Map)}.
   *
   * @return An immutable map containing the written contents.
   */
  public Map<String, String> readMapOfStrings() throws IOException {
    int count = readVInt();
    if (count == 0) {
      return Collections.emptyMap();
    } else if (count == 1) {
      return Collections.singletonMap(readString(), readString());
    } else {
      Map<String, String> map = count > 10 ? new HashMap<>() : new TreeMap<>();
      for (int i = 0; i < count; i++) {
        final String key = readString();
        final String val = readString();
        map.put(key, val);
      }
      return Collections.unmodifiableMap(map);
    }
  }

  /**
   * Reads a Set&lt;String&gt; previously written with {@link DataOutput#writeSetOfStrings(Set)}.
   *
   * @return An immutable set containing the written contents.
   */
  public Set<String> readSetOfStrings() throws IOException {
    int count = readVInt();
    if (count == 0) {
      return Collections.emptySet();
    } else if (count == 1) {
      return Collections.singleton(readString());
    } else {
      Set<String> set = count > 10 ? new HashSet<>() : new TreeSet<>();
      for (int i = 0; i < count; i++) {
        set.add(readString());
      }
      return Collections.unmodifiableSet(set);
    }
  }

  /**
   * Skip over <code>numBytes</code> bytes. This method may skip bytes in whatever way is most
   * optimal, and may not have the same behavior as reading the skipped bytes. In general, negative
   * <code>numBytes</code> are not supported.
   */
  public abstract void skipBytes(final long numBytes) throws IOException;
}
