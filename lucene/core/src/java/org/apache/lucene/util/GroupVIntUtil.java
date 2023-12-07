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
package org.apache.lucene.util;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.IndexInput;

/**
 * This class contains utility methods and constants for group varint
 *
 * @lucene.internal
 */
public final class GroupVIntUtil {
  // the maximum length of a single group-varint is 4 integers + 1 byte flag.
  public static final int MAX_LENGTH_PER_GROUP = 17;
  public static final int[] GROUP_VINT_MASKS = new int[] {0xFF, 0xFFFF, 0xFFFFFF, 0xFFFFFFFF};

  /**
   * Default implementation of read single group, for optimal performance, you should use {@link
   * DataInput#readGroupVInts(long[], int)} instead.
   *
   * @param dst the array to read ints into.
   * @param offset the offset in the array to start storing ints.
   */
  public static void readGroupVInt(DataInput in, long[] dst, int offset) throws IOException {
    final int flag = in.readByte() & 0xFF;

    final int n1Minus1 = flag >> 6;
    final int n2Minus1 = (flag >> 4) & 0x03;
    final int n3Minus1 = (flag >> 2) & 0x03;
    final int n4Minus1 = flag & 0x03;

    dst[offset] = readLongInGroup(in, n1Minus1);
    dst[offset + 1] = readLongInGroup(in, n2Minus1);
    dst[offset + 2] = readLongInGroup(in, n3Minus1);
    dst[offset + 3] = readLongInGroup(in, n4Minus1);
  }

  private static long readLongInGroup(DataInput in, int numBytesMinus1) throws IOException {
    switch (numBytesMinus1) {
      case 0:
        return in.readByte() & 0xFFL;
      case 1:
        return in.readShort() & 0xFFFFL;
      case 2:
        return (in.readShort() & 0xFFFFL) | ((in.readByte() & 0xFFL) << 16);
      default:
        return in.readInt() & 0xFFFFFFFFL;
    }
  }

  public static void readGroupVInt(ByteBuffer src, long[] dst, int offset) throws IOException {
    assert src.remaining() >= MAX_LENGTH_PER_GROUP;

    final int flag = src.get() & 0xFF;
    final int n1Minus1 = flag >> 6;
    final int n2Minus1 = (flag >> 4) & 0x03;
    final int n3Minus1 = (flag >> 2) & 0x03;
    final int n4Minus1 = flag & 0x03;

    // This code path has fewer conditionals and tends to be significantly faster in benchmarks
    int pos = src.position();
    dst[offset] = src.getInt(pos) & GROUP_VINT_MASKS[n1Minus1];
    pos +=  1 + n1Minus1;
    dst[offset + 1] = src.getInt(pos) & GROUP_VINT_MASKS[n2Minus1];
    pos += 1 + n2Minus1;
    dst[offset + 2] = src.getInt(pos) & GROUP_VINT_MASKS[n3Minus1];
    pos += 1 + n3Minus1;
    dst[offset + 3] = src.getInt(pos) & GROUP_VINT_MASKS[n4Minus1];
    pos += 1 + n4Minus1;
    src.position(pos);
  }

  public static void readGroupVInts(IndexInput in, long[] dst, int limit) throws IOException {
    int i;
    for (i = 0; i <= limit - 4; i += 4) {
      try {
        ByteBuffer buf = in.readNBytes(MAX_LENGTH_PER_GROUP);
        readGroupVInt(buf, dst, i);
        in.seek(in.getFilePointer() - buf.remaining()); // rewind unread bytes
      } catch (@SuppressWarnings("unused") EOFException|IndexOutOfBoundsException e) {
        // TODO: what are the right exception types
        readGroupVInt(in, dst, i);
      }
    }
    for (; i < limit; ++i) {
      dst[i] = in.readVInt();
    }
  }
}
