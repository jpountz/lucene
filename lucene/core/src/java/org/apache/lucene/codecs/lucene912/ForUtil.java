// This file has been automatically generated, DO NOT EDIT

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
package org.apache.lucene.codecs.lucene912;

import java.io.IOException;
import org.apache.lucene.internal.vectorization.PostingDecodingUtil;
import org.apache.lucene.store.DataOutput;

/**
 * Inspired from https://fulmicoton.com/posts/bitpacking/ Encodes multiple integers in a long to get
 * SIMD-like speedups. If bitsPerValue &lt;= 8 then we pack 8 ints per long else if bitsPerValue
 * &lt;= 16 we pack 4 ints per long else we pack 2 ints per long
 */
public final class ForUtil {

  public static final int BLOCK_SIZE = 128;
  static final int BLOCK_SIZE_LOG2 = 7;

  static long expandMask32(long mask32) {
    return mask32 | (mask32 << 32);
  }

  static long expandMask16(long mask16) {
    return expandMask32(mask16 | (mask16 << 16));
  }

  static long expandMask8(long mask8) {
    return expandMask16(mask8 | (mask8 << 8));
  }

  static long mask32(int bitsPerValue) {
    return expandMask32((1L << bitsPerValue) - 1);
  }

  static long mask16(int bitsPerValue) {
    return expandMask16((1L << bitsPerValue) - 1);
  }

  static long mask8(int bitsPerValue) {
    return expandMask8((1L << bitsPerValue) - 1);
  }

  static void expand8(long[] arr) {
    for (int i = 0; i < 16; ++i) {
      long l = arr[i];
      arr[i] = (l >>> 56) & 0xFFL;
      arr[16 + i] = (l >>> 48) & 0xFFL;
      arr[32 + i] = (l >>> 40) & 0xFFL;
      arr[48 + i] = (l >>> 32) & 0xFFL;
      arr[64 + i] = (l >>> 24) & 0xFFL;
      arr[80 + i] = (l >>> 16) & 0xFFL;
      arr[96 + i] = (l >>> 8) & 0xFFL;
      arr[112 + i] = l & 0xFFL;
    }
  }

  static void collapse8(long[] arr) {
    for (int i = 0; i < 16; ++i) {
      arr[i] =
          (arr[i] << 56)
              | (arr[16 + i] << 48)
              | (arr[32 + i] << 40)
              | (arr[48 + i] << 32)
              | (arr[64 + i] << 24)
              | (arr[80 + i] << 16)
              | (arr[96 + i] << 8)
              | arr[112 + i];
    }
  }

  static void expand16(long[] arr) {
    for (int i = 0; i < 32; ++i) {
      long l = arr[i];
      arr[i] = (l >>> 48) & 0xFFFFL;
      arr[32 + i] = (l >>> 32) & 0xFFFFL;
      arr[64 + i] = (l >>> 16) & 0xFFFFL;
      arr[96 + i] = l & 0xFFFFL;
    }
  }

  static void collapse16(long[] arr) {
    for (int i = 0; i < 32; ++i) {
      arr[i] = (arr[i] << 48) | (arr[32 + i] << 32) | (arr[64 + i] << 16) | arr[96 + i];
    }
  }

  static void expand32(long[] arr) {
    for (int i = 0; i < 64; ++i) {
      long l = arr[i];
      arr[i] = l >>> 32;
      arr[64 + i] = l & 0xFFFFFFFFL;
    }
  }

  static void collapse32(long[] arr) {
    for (int i = 0; i < 64; ++i) {
      arr[i] = (arr[i] << 32) | arr[64 + i];
    }
  }

  private final long[] tmp = new long[BLOCK_SIZE / 2];

  /** Encode 128 integers from {@code longs} into {@code out}. */
  void encode(long[] longs, int bitsPerValue, DataOutput out) throws IOException {
    final int nextPrimitive;
    if (bitsPerValue <= 8) {
      nextPrimitive = 8;
      collapse8(longs);
    } else if (bitsPerValue <= 16) {
      nextPrimitive = 16;
      collapse16(longs);
    } else {
      nextPrimitive = 32;
      collapse32(longs);
    }
    encode(longs, bitsPerValue, nextPrimitive, out, tmp);
  }

  static void encode(long[] longs, int bitsPerValue, int primitiveSize, DataOutput out, long[] tmp)
      throws IOException {
    final int numLongs = BLOCK_SIZE * primitiveSize / Long.SIZE;

    final int numLongsPerShift = bitsPerValue * 2;
    int idx = 0;
    int shift = primitiveSize - bitsPerValue;
    for (int i = 0; i < numLongsPerShift; ++i) {
      tmp[i] = longs[idx++] << shift;
    }
    for (shift = shift - bitsPerValue; shift >= 0; shift -= bitsPerValue) {
      for (int i = 0; i < numLongsPerShift; ++i) {
        tmp[i] |= longs[idx++] << shift;
      }
    }

    final int remainingBitsPerLong = shift + bitsPerValue;
    final long maskRemainingBitsPerLong;
    if (primitiveSize == 8) {
      maskRemainingBitsPerLong = MASKS8[remainingBitsPerLong];
    } else if (primitiveSize == 16) {
      maskRemainingBitsPerLong = MASKS16[remainingBitsPerLong];
    } else {
      maskRemainingBitsPerLong = MASKS32[remainingBitsPerLong];
    }

    int tmpIdx = 0;
    int remainingBitsPerValue = bitsPerValue;
    while (idx < numLongs) {
      if (remainingBitsPerValue >= remainingBitsPerLong) {
        remainingBitsPerValue -= remainingBitsPerLong;
        tmp[tmpIdx++] |= (longs[idx] >>> remainingBitsPerValue) & maskRemainingBitsPerLong;
        if (remainingBitsPerValue == 0) {
          idx++;
          remainingBitsPerValue = bitsPerValue;
        }
      } else {
        final long mask1, mask2;
        if (primitiveSize == 8) {
          mask1 = MASKS8[remainingBitsPerValue];
          mask2 = MASKS8[remainingBitsPerLong - remainingBitsPerValue];
        } else if (primitiveSize == 16) {
          mask1 = MASKS16[remainingBitsPerValue];
          mask2 = MASKS16[remainingBitsPerLong - remainingBitsPerValue];
        } else {
          mask1 = MASKS32[remainingBitsPerValue];
          mask2 = MASKS32[remainingBitsPerLong - remainingBitsPerValue];
        }
        tmp[tmpIdx] |= (longs[idx++] & mask1) << (remainingBitsPerLong - remainingBitsPerValue);
        remainingBitsPerValue = bitsPerValue - remainingBitsPerLong + remainingBitsPerValue;
        tmp[tmpIdx++] |= (longs[idx] >>> remainingBitsPerValue) & mask2;
      }
    }

    for (int i = 0; i < numLongsPerShift; ++i) {
      out.writeLong(tmp[i]);
    }
  }

  /** Number of bytes required to encode 128 integers of {@code bitsPerValue} bits per value. */
  static int numBytes(int bitsPerValue) {
    return bitsPerValue << (BLOCK_SIZE_LOG2 - 3);
  }

  static void decodeSlow(int bitsPerValue, PostingDecodingUtil pdu, long[] tmp, long[] longs)
      throws IOException {
    final int numLongs = bitsPerValue << 1;
    final long mask = MASKS32[bitsPerValue];
    pdu.splitLongs1(numLongs, longs, 32 - bitsPerValue, mask, tmp, 0, -1L);
    final int remainingBitsPerLong = 32 - bitsPerValue;
    final long mask32RemainingBitsPerLong = MASKS32[remainingBitsPerLong];
    int tmpIdx = 0;
    int remainingBits = remainingBitsPerLong;
    for (int longsIdx = numLongs; longsIdx < BLOCK_SIZE / 2; ++longsIdx) {
      int b = bitsPerValue - remainingBits;
      long l = (tmp[tmpIdx++] & MASKS32[remainingBits]) << b;
      while (b >= remainingBitsPerLong) {
        b -= remainingBitsPerLong;
        l |= (tmp[tmpIdx++] & mask32RemainingBitsPerLong) << b;
      }
      if (b > 0) {
        l |= (tmp[tmpIdx] >>> (remainingBitsPerLong - b)) & MASKS32[b];
        remainingBits = remainingBitsPerLong - b;
      } else {
        remainingBits = remainingBitsPerLong;
      }
      longs[longsIdx] = l;
    }
  }

  static final long[] MASKS8 = new long[8];
  static final long[] MASKS16 = new long[16];
  static final long[] MASKS32 = new long[32];

  static {
    for (int i = 0; i < 8; ++i) {
      MASKS8[i] = mask8(i);
    }
    for (int i = 0; i < 16; ++i) {
      MASKS16[i] = mask16(i);
    }
    for (int i = 0; i < 32; ++i) {
      MASKS32[i] = mask32(i);
    }
  }

  // mark values in array as final longs to avoid the cost of reading array, arrays should only be
  // used when the idx is a variable
  static final long MASK8_1 = MASKS8[1];
  static final long MASK8_2 = MASKS8[2];
  static final long MASK8_3 = MASKS8[3];
  static final long MASK8_4 = MASKS8[4];
  static final long MASK8_5 = MASKS8[5];
  static final long MASK8_6 = MASKS8[6];
  static final long MASK8_7 = MASKS8[7];
  static final long MASK16_1 = MASKS16[1];
  static final long MASK16_2 = MASKS16[2];
  static final long MASK16_3 = MASKS16[3];
  static final long MASK16_4 = MASKS16[4];
  static final long MASK16_5 = MASKS16[5];
  static final long MASK16_6 = MASKS16[6];
  static final long MASK16_7 = MASKS16[7];
  static final long MASK16_8 = MASKS16[8];
  static final long MASK16_9 = MASKS16[9];
  static final long MASK16_10 = MASKS16[10];
  static final long MASK16_11 = MASKS16[11];
  static final long MASK16_12 = MASKS16[12];
  static final long MASK16_13 = MASKS16[13];
  static final long MASK16_14 = MASKS16[14];
  static final long MASK16_15 = MASKS16[15];
  static final long MASK32_1 = MASKS32[1];
  static final long MASK32_2 = MASKS32[2];
  static final long MASK32_3 = MASKS32[3];
  static final long MASK32_4 = MASKS32[4];
  static final long MASK32_5 = MASKS32[5];
  static final long MASK32_6 = MASKS32[6];
  static final long MASK32_7 = MASKS32[7];
  static final long MASK32_8 = MASKS32[8];
  static final long MASK32_9 = MASKS32[9];
  static final long MASK32_10 = MASKS32[10];
  static final long MASK32_11 = MASKS32[11];
  static final long MASK32_12 = MASKS32[12];
  static final long MASK32_13 = MASKS32[13];
  static final long MASK32_14 = MASKS32[14];
  static final long MASK32_15 = MASKS32[15];
  static final long MASK32_16 = MASKS32[16];

  @FunctionalInterface
  private interface Decoder {
    void decode(PostingDecodingUtil pdu, long[] tmp, long[] longs) throws IOException;
  }

  private static Decoder[] DECODERS = new Decoder[17];

  static {
    DECODERS[1] = ForUtil::decode1;
    DECODERS[2] = ForUtil::decode2;
    DECODERS[3] = ForUtil::decode3;
    DECODERS[4] = ForUtil::decode4;
    DECODERS[5] = ForUtil::decode5;
    DECODERS[6] = ForUtil::decode6;
    DECODERS[7] = ForUtil::decode7;
    DECODERS[8] = ForUtil::decode8;
    DECODERS[9] = ForUtil::decode9;
    DECODERS[10] = ForUtil::decode10;
    DECODERS[11] = ForUtil::decode11;
    DECODERS[12] = ForUtil::decode12;
    DECODERS[13] = ForUtil::decode13;
    DECODERS[14] = ForUtil::decode14;
    DECODERS[15] = ForUtil::decode15;
    DECODERS[16] = ForUtil::decode16;
  }

  /** Decode 128 integers into {@code longs}. */
  void decode(int bitsPerValue, PostingDecodingUtil pdu, long[] longs) throws IOException {
    if (bitsPerValue <= 16) {
      DECODERS[bitsPerValue].decode(pdu, tmp, longs);
    } else {
      decodeSlow(bitsPerValue, pdu, tmp, longs);
      expand32(longs);
    }
  }

  private static void decode1(PostingDecodingUtil pdu, long[] tmp, long[] longs)
      throws IOException {
    pdu.in.readLongs(tmp, 0, 2);
    for (int i = 0; i < 2; ++i) {
      longs[0 + i] = (tmp[i] >>> 7) & MASK8_1;
      longs[2 + i] = (tmp[i] >>> 6) & MASK8_1;
      longs[4 + i] = (tmp[i] >>> 5) & MASK8_1;
      longs[6 + i] = (tmp[i] >>> 4) & MASK8_1;
      longs[8 + i] = (tmp[i] >>> 3) & MASK8_1;
      longs[10 + i] = (tmp[i] >>> 2) & MASK8_1;
      longs[12 + i] = (tmp[i] >>> 1) & MASK8_1;
      longs[14 + i] = (tmp[i] >>> 0) & MASK8_1;
    }
    expand8(longs);
  }

  private static void decode2(PostingDecodingUtil pdu, long[] tmp, long[] longs)
      throws IOException {
    pdu.splitLongs3(4, longs, 6, 4, 2, MASK8_2, longs, 12, MASK8_2);
    expand8(longs);
  }

  private static void decode3(PostingDecodingUtil pdu, long[] tmp, long[] longs)
      throws IOException {
    pdu.splitLongs2(6, longs, 5, 2, MASK8_3, tmp, 0, MASK8_2);
    decode3To8Remainder(tmp, longs);
    expand8(longs);
  }

  static void decode3To8Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 12; iter < 2; ++iter, tmpIdx += 3, longsIdx += 2) {
      long l0 = tmp[tmpIdx + 0] << 1;
      l0 |= (tmp[tmpIdx + 1] >>> 1) & MASK8_1;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 1] & MASK8_1) << 2;
      l1 |= tmp[tmpIdx + 2] << 0;
      longs[longsIdx + 1] = l1;
    }
  }

  private static void decode4(PostingDecodingUtil pdu, long[] tmp, long[] longs)
      throws IOException {
    pdu.splitLongs1(8, longs, 4, MASK8_4, longs, 8, MASK8_4);
    expand8(longs);
  }

  private static void decode5(PostingDecodingUtil pdu, long[] tmp, long[] longs)
      throws IOException {
    pdu.splitLongs1(10, longs, 3, MASK8_5, tmp, 0, MASK8_3);
    decode5To8Remainder(tmp, longs);
    expand8(longs);
  }

  static void decode5To8Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 10; iter < 2; ++iter, tmpIdx += 5, longsIdx += 3) {
      long l0 = tmp[tmpIdx + 0] << 2;
      l0 |= (tmp[tmpIdx + 1] >>> 1) & MASK8_2;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 1] & MASK8_1) << 4;
      l1 |= tmp[tmpIdx + 2] << 1;
      l1 |= (tmp[tmpIdx + 3] >>> 2) & MASK8_1;
      longs[longsIdx + 1] = l1;
      long l2 = (tmp[tmpIdx + 3] & MASK8_2) << 3;
      l2 |= tmp[tmpIdx + 4] << 0;
      longs[longsIdx + 2] = l2;
    }
  }

  private static void decode6(PostingDecodingUtil pdu, long[] tmp, long[] longs)
      throws IOException {
    pdu.splitLongs1(12, longs, 2, MASK8_6, tmp, 0, MASK8_2);
    decode6To8Remainder(tmp, longs);
    expand8(longs);
  }

  static void decode6To8Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 12; iter < 4; ++iter, tmpIdx += 3, longsIdx += 1) {
      long l0 = tmp[tmpIdx + 0] << 4;
      l0 |= tmp[tmpIdx + 1] << 2;
      l0 |= tmp[tmpIdx + 2] << 0;
      longs[longsIdx + 0] = l0;
    }
  }

  private static void decode7(PostingDecodingUtil pdu, long[] tmp, long[] longs)
      throws IOException {
    pdu.splitLongs1(14, longs, 1, MASK8_7, tmp, 0, MASK8_1);
    decode7To8Remainder(tmp, longs);
    expand8(longs);
  }

  static void decode7To8Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 14; iter < 2; ++iter, tmpIdx += 7, longsIdx += 1) {
      long l0 = tmp[tmpIdx + 0] << 6;
      l0 |= tmp[tmpIdx + 1] << 5;
      l0 |= tmp[tmpIdx + 2] << 4;
      l0 |= tmp[tmpIdx + 3] << 3;
      l0 |= tmp[tmpIdx + 4] << 2;
      l0 |= tmp[tmpIdx + 5] << 1;
      l0 |= tmp[tmpIdx + 6] << 0;
      longs[longsIdx + 0] = l0;
    }
  }

  private static void decode8(PostingDecodingUtil pdu, long[] tmp, long[] longs)
      throws IOException {
    pdu.in.readLongs(longs, 0, 16);
    expand8(longs);
  }

  private static void decode9(PostingDecodingUtil pdu, long[] tmp, long[] longs)
      throws IOException {
    pdu.splitLongs1(18, longs, 7, MASK16_9, tmp, 0, MASK16_7);
    decode9To16Remainder(tmp, longs);
    expand16(longs);
  }

  static void decode9To16Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 18; iter < 2; ++iter, tmpIdx += 9, longsIdx += 7) {
      long l0 = tmp[tmpIdx + 0] << 2;
      l0 |= (tmp[tmpIdx + 1] >>> 5) & MASK16_2;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 1] & MASK16_5) << 4;
      l1 |= (tmp[tmpIdx + 2] >>> 3) & MASK16_4;
      longs[longsIdx + 1] = l1;
      long l2 = (tmp[tmpIdx + 2] & MASK16_3) << 6;
      l2 |= (tmp[tmpIdx + 3] >>> 1) & MASK16_6;
      longs[longsIdx + 2] = l2;
      long l3 = (tmp[tmpIdx + 3] & MASK16_1) << 8;
      l3 |= tmp[tmpIdx + 4] << 1;
      l3 |= (tmp[tmpIdx + 5] >>> 6) & MASK16_1;
      longs[longsIdx + 3] = l3;
      long l4 = (tmp[tmpIdx + 5] & MASK16_6) << 3;
      l4 |= (tmp[tmpIdx + 6] >>> 4) & MASK16_3;
      longs[longsIdx + 4] = l4;
      long l5 = (tmp[tmpIdx + 6] & MASK16_4) << 5;
      l5 |= (tmp[tmpIdx + 7] >>> 2) & MASK16_5;
      longs[longsIdx + 5] = l5;
      long l6 = (tmp[tmpIdx + 7] & MASK16_2) << 7;
      l6 |= tmp[tmpIdx + 8] << 0;
      longs[longsIdx + 6] = l6;
    }
  }

  private static void decode10(PostingDecodingUtil pdu, long[] tmp, long[] longs)
      throws IOException {
    pdu.splitLongs1(20, longs, 6, MASK16_10, tmp, 0, MASK16_6);
    decode10To16Remainder(tmp, longs);
    expand16(longs);
  }

  static void decode10To16Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 20; iter < 4; ++iter, tmpIdx += 5, longsIdx += 3) {
      long l0 = tmp[tmpIdx + 0] << 4;
      l0 |= (tmp[tmpIdx + 1] >>> 2) & MASK16_4;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 1] & MASK16_2) << 8;
      l1 |= tmp[tmpIdx + 2] << 2;
      l1 |= (tmp[tmpIdx + 3] >>> 4) & MASK16_2;
      longs[longsIdx + 1] = l1;
      long l2 = (tmp[tmpIdx + 3] & MASK16_4) << 6;
      l2 |= tmp[tmpIdx + 4] << 0;
      longs[longsIdx + 2] = l2;
    }
  }

  private static void decode11(PostingDecodingUtil pdu, long[] tmp, long[] longs)
      throws IOException {
    pdu.splitLongs1(22, longs, 5, MASK16_11, tmp, 0, MASK16_5);
    decode11To16Remainder(tmp, longs);
    expand16(longs);
  }

  static void decode11To16Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 22; iter < 2; ++iter, tmpIdx += 11, longsIdx += 5) {
      long l0 = tmp[tmpIdx + 0] << 6;
      l0 |= tmp[tmpIdx + 1] << 1;
      l0 |= (tmp[tmpIdx + 2] >>> 4) & MASK16_1;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 2] & MASK16_4) << 7;
      l1 |= tmp[tmpIdx + 3] << 2;
      l1 |= (tmp[tmpIdx + 4] >>> 3) & MASK16_2;
      longs[longsIdx + 1] = l1;
      long l2 = (tmp[tmpIdx + 4] & MASK16_3) << 8;
      l2 |= tmp[tmpIdx + 5] << 3;
      l2 |= (tmp[tmpIdx + 6] >>> 2) & MASK16_3;
      longs[longsIdx + 2] = l2;
      long l3 = (tmp[tmpIdx + 6] & MASK16_2) << 9;
      l3 |= tmp[tmpIdx + 7] << 4;
      l3 |= (tmp[tmpIdx + 8] >>> 1) & MASK16_4;
      longs[longsIdx + 3] = l3;
      long l4 = (tmp[tmpIdx + 8] & MASK16_1) << 10;
      l4 |= tmp[tmpIdx + 9] << 5;
      l4 |= tmp[tmpIdx + 10] << 0;
      longs[longsIdx + 4] = l4;
    }
  }

  private static void decode12(PostingDecodingUtil pdu, long[] tmp, long[] longs)
      throws IOException {
    pdu.splitLongs1(24, longs, 4, MASK16_12, tmp, 0, MASK16_4);
    decode12To16Remainder(tmp, longs);
    expand16(longs);
  }

  static void decode12To16Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 24; iter < 8; ++iter, tmpIdx += 3, longsIdx += 1) {
      long l0 = tmp[tmpIdx + 0] << 8;
      l0 |= tmp[tmpIdx + 1] << 4;
      l0 |= tmp[tmpIdx + 2] << 0;
      longs[longsIdx + 0] = l0;
    }
  }

  private static void decode13(PostingDecodingUtil pdu, long[] tmp, long[] longs)
      throws IOException {
    pdu.splitLongs1(26, longs, 3, MASK16_13, tmp, 0, MASK16_3);
    decode13To16Remainder(tmp, longs);
    expand16(longs);
  }

  static void decode13To16Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 26; iter < 2; ++iter, tmpIdx += 13, longsIdx += 3) {
      long l0 = tmp[tmpIdx + 0] << 10;
      l0 |= tmp[tmpIdx + 1] << 7;
      l0 |= tmp[tmpIdx + 2] << 4;
      l0 |= tmp[tmpIdx + 3] << 1;
      l0 |= (tmp[tmpIdx + 4] >>> 2) & MASK16_1;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 4] & MASK16_2) << 11;
      l1 |= tmp[tmpIdx + 5] << 8;
      l1 |= tmp[tmpIdx + 6] << 5;
      l1 |= tmp[tmpIdx + 7] << 2;
      l1 |= (tmp[tmpIdx + 8] >>> 1) & MASK16_2;
      longs[longsIdx + 1] = l1;
      long l2 = (tmp[tmpIdx + 8] & MASK16_1) << 12;
      l2 |= tmp[tmpIdx + 9] << 9;
      l2 |= tmp[tmpIdx + 10] << 6;
      l2 |= tmp[tmpIdx + 11] << 3;
      l2 |= tmp[tmpIdx + 12] << 0;
      longs[longsIdx + 2] = l2;
    }
  }

  private static void decode14(PostingDecodingUtil pdu, long[] tmp, long[] longs)
      throws IOException {
    pdu.splitLongs1(28, longs, 2, MASK16_14, tmp, 0, MASK16_2);
    decode14To16Remainder(tmp, longs);
    expand16(longs);
  }

  static void decode14To16Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 28; iter < 4; ++iter, tmpIdx += 7, longsIdx += 1) {
      long l0 = tmp[tmpIdx + 0] << 12;
      l0 |= tmp[tmpIdx + 1] << 10;
      l0 |= tmp[tmpIdx + 2] << 8;
      l0 |= tmp[tmpIdx + 3] << 6;
      l0 |= tmp[tmpIdx + 4] << 4;
      l0 |= tmp[tmpIdx + 5] << 2;
      l0 |= tmp[tmpIdx + 6] << 0;
      longs[longsIdx + 0] = l0;
    }
  }

  private static void decode15(PostingDecodingUtil pdu, long[] tmp, long[] longs)
      throws IOException {
    pdu.splitLongs1(30, longs, 1, MASK16_15, tmp, 0, MASK16_1);
    decode15To16Remainder(tmp, longs);
    expand16(longs);
  }

  static void decode15To16Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 30; iter < 2; ++iter, tmpIdx += 15, longsIdx += 1) {
      long l0 = tmp[tmpIdx + 0] << 14;
      l0 |= tmp[tmpIdx + 1] << 13;
      l0 |= tmp[tmpIdx + 2] << 12;
      l0 |= tmp[tmpIdx + 3] << 11;
      l0 |= tmp[tmpIdx + 4] << 10;
      l0 |= tmp[tmpIdx + 5] << 9;
      l0 |= tmp[tmpIdx + 6] << 8;
      l0 |= tmp[tmpIdx + 7] << 7;
      l0 |= tmp[tmpIdx + 8] << 6;
      l0 |= tmp[tmpIdx + 9] << 5;
      l0 |= tmp[tmpIdx + 10] << 4;
      l0 |= tmp[tmpIdx + 11] << 3;
      l0 |= tmp[tmpIdx + 12] << 2;
      l0 |= tmp[tmpIdx + 13] << 1;
      l0 |= tmp[tmpIdx + 14] << 0;
      longs[longsIdx + 0] = l0;
    }
  }

  private static void decode16(PostingDecodingUtil pdu, long[] tmp, long[] longs)
      throws IOException {
    pdu.in.readLongs(longs, 0, 32);
    expand16(longs);
  }
}
