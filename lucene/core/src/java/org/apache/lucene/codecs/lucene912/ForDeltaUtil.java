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

import static org.apache.lucene.codecs.lucene912.ForUtil.*;

import java.io.IOException;
import org.apache.lucene.internal.vectorization.PostingDecodingUtil;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.packed.PackedInts;

/**
 * Inspired from https://fulmicoton.com/posts/bitpacking/ Encodes multiple integers in a long to get
 * SIMD-like speedups. If bitsPerValue &lt;= 4 then we pack 8 ints per long else if bitsPerValue
 * &lt;= 11 we pack 4 ints per long else we pack 2 ints per long
 */
public final class ForDeltaUtil {

  private static final int ONE_BLOCK_SIZE_FOURTH = BLOCK_SIZE / 4;
  private static final int TWO_BLOCK_SIZE_FOURTHS = BLOCK_SIZE / 2;
  private static final int THREE_BLOCK_SIZE_FOURTHS = 3 * BLOCK_SIZE / 4;

  private static final int ONE_BLOCK_SIZE_EIGHT = BLOCK_SIZE / 8;
  private static final int TWO_BLOCK_SIZE_EIGHTS = BLOCK_SIZE / 4;
  private static final int THREE_BLOCK_SIZE_EIGHTS = 3 * BLOCK_SIZE / 8;
  private static final int FOUR_BLOCK_SIZE_EIGHTS = BLOCK_SIZE / 2;
  private static final int FIVE_BLOCK_SIZE_EIGHTS = 5 * BLOCK_SIZE / 8;
  private static final int SIX_BLOCK_SIZE_EIGHTS = 3 * BLOCK_SIZE / 4;
  private static final int SEVEN_BLOCK_SIZE_EIGHTS = 7 * BLOCK_SIZE / 8;

  // IDENTITY_PLUS_ONE[i] == i+1
  private static final long[] IDENTITY_PLUS_ONE = new long[ForUtil.BLOCK_SIZE];

  static {
    for (int i = 0; i < ForUtil.BLOCK_SIZE; ++i) {
      IDENTITY_PLUS_ONE[i] = i + 1;
    }
  }

  private static void prefixSumOfOnes(long[] arr, long base) {
    System.arraycopy(IDENTITY_PLUS_ONE, 0, arr, 0, ForUtil.BLOCK_SIZE);
    // This loop gets auto-vectorized
    for (int i = 0; i < ForUtil.BLOCK_SIZE; ++i) {
      arr[i] += base;
    }
  }

  private static void prefixSum8(long[] arr, long base) {
    // When the number of bits per value is 4 or less, we can sum up all values in a block without
    // risking overflowing a 8-bits integer. This allows computing the prefix sum by summing up 8
    // values at once.
    innerPrefixSum8(arr);
    expand8(arr);
    final long l0 = base;
    final long l1 = l0 + arr[ONE_BLOCK_SIZE_EIGHT - 1];
    final long l2 = l1 + arr[TWO_BLOCK_SIZE_EIGHTS - 1];
    final long l3 = l2 + arr[THREE_BLOCK_SIZE_EIGHTS - 1];
    final long l4 = l3 + arr[FOUR_BLOCK_SIZE_EIGHTS - 1];
    final long l5 = l4 + arr[FIVE_BLOCK_SIZE_EIGHTS - 1];
    final long l6 = l5 + arr[SIX_BLOCK_SIZE_EIGHTS - 1];
    final long l7 = l6 + arr[SEVEN_BLOCK_SIZE_EIGHTS - 1];

    for (int i = 0; i < ONE_BLOCK_SIZE_EIGHT; ++i) {
      arr[i] += l0;
      arr[ONE_BLOCK_SIZE_EIGHT + i] += l1;
      arr[TWO_BLOCK_SIZE_EIGHTS + i] += l2;
      arr[THREE_BLOCK_SIZE_EIGHTS + i] += l3;
      arr[FOUR_BLOCK_SIZE_EIGHTS + i] += l4;
      arr[FIVE_BLOCK_SIZE_EIGHTS + i] += l5;
      arr[SIX_BLOCK_SIZE_EIGHTS + i] += l6;
      arr[SEVEN_BLOCK_SIZE_EIGHTS + i] += l7;
    }
  }

  private static void prefixSum16(long[] arr, long base) {
    // When the number of bits per value is 11 or less, we can sum up all values in a block without
    // risking overflowing a 16-bits integer. This allows computing the prefix sum by summing up 4
    // values at once.
    innerPrefixSum16(arr);
    expand16(arr);
    final long l0 = base;
    final long l1 = l0 + arr[ONE_BLOCK_SIZE_FOURTH - 1];
    final long l2 = l1 + arr[TWO_BLOCK_SIZE_FOURTHS - 1];
    final long l3 = l2 + arr[THREE_BLOCK_SIZE_FOURTHS - 1];

    for (int i = 0; i < ONE_BLOCK_SIZE_FOURTH; ++i) {
      arr[i] += l0;
      arr[ONE_BLOCK_SIZE_FOURTH + i] += l1;
      arr[TWO_BLOCK_SIZE_FOURTHS + i] += l2;
      arr[THREE_BLOCK_SIZE_FOURTHS + i] += l3;
    }
  }

  private static void prefixSum32(long[] arr, long base) {
    arr[0] += base << 32;
    innerPrefixSum32(arr);
    expand32(arr);
    final long l = arr[BLOCK_SIZE / 2 - 1];
    for (int i = BLOCK_SIZE / 2; i < BLOCK_SIZE; ++i) {
      arr[i] += l;
    }
  }

  // For some reason unrolling seems to help
  private static void innerPrefixSum8(long[] arr) {
    arr[1] += arr[0];
    arr[2] += arr[1];
    arr[3] += arr[2];
    arr[4] += arr[3];
    arr[5] += arr[4];
    arr[6] += arr[5];
    arr[7] += arr[6];
    arr[8] += arr[7];
    arr[9] += arr[8];
    arr[10] += arr[9];
    arr[11] += arr[10];
    arr[12] += arr[11];
    arr[13] += arr[12];
    arr[14] += arr[13];
    arr[15] += arr[14];
  }

  // For some reason unrolling seems to help
  private static void innerPrefixSum16(long[] arr) {
    arr[1] += arr[0];
    arr[2] += arr[1];
    arr[3] += arr[2];
    arr[4] += arr[3];
    arr[5] += arr[4];
    arr[6] += arr[5];
    arr[7] += arr[6];
    arr[8] += arr[7];
    arr[9] += arr[8];
    arr[10] += arr[9];
    arr[11] += arr[10];
    arr[12] += arr[11];
    arr[13] += arr[12];
    arr[14] += arr[13];
    arr[15] += arr[14];
    arr[16] += arr[15];
    arr[17] += arr[16];
    arr[18] += arr[17];
    arr[19] += arr[18];
    arr[20] += arr[19];
    arr[21] += arr[20];
    arr[22] += arr[21];
    arr[23] += arr[22];
    arr[24] += arr[23];
    arr[25] += arr[24];
    arr[26] += arr[25];
    arr[27] += arr[26];
    arr[28] += arr[27];
    arr[29] += arr[28];
    arr[30] += arr[29];
    arr[31] += arr[30];
  }

  // For some reason unrolling seems to help
  private static void innerPrefixSum32(long[] arr) {
    arr[1] += arr[0];
    arr[2] += arr[1];
    arr[3] += arr[2];
    arr[4] += arr[3];
    arr[5] += arr[4];
    arr[6] += arr[5];
    arr[7] += arr[6];
    arr[8] += arr[7];
    arr[9] += arr[8];
    arr[10] += arr[9];
    arr[11] += arr[10];
    arr[12] += arr[11];
    arr[13] += arr[12];
    arr[14] += arr[13];
    arr[15] += arr[14];
    arr[16] += arr[15];
    arr[17] += arr[16];
    arr[18] += arr[17];
    arr[19] += arr[18];
    arr[20] += arr[19];
    arr[21] += arr[20];
    arr[22] += arr[21];
    arr[23] += arr[22];
    arr[24] += arr[23];
    arr[25] += arr[24];
    arr[26] += arr[25];
    arr[27] += arr[26];
    arr[28] += arr[27];
    arr[29] += arr[28];
    arr[30] += arr[29];
    arr[31] += arr[30];
    arr[32] += arr[31];
    arr[33] += arr[32];
    arr[34] += arr[33];
    arr[35] += arr[34];
    arr[36] += arr[35];
    arr[37] += arr[36];
    arr[38] += arr[37];
    arr[39] += arr[38];
    arr[40] += arr[39];
    arr[41] += arr[40];
    arr[42] += arr[41];
    arr[43] += arr[42];
    arr[44] += arr[43];
    arr[45] += arr[44];
    arr[46] += arr[45];
    arr[47] += arr[46];
    arr[48] += arr[47];
    arr[49] += arr[48];
    arr[50] += arr[49];
    arr[51] += arr[50];
    arr[52] += arr[51];
    arr[53] += arr[52];
    arr[54] += arr[53];
    arr[55] += arr[54];
    arr[56] += arr[55];
    arr[57] += arr[56];
    arr[58] += arr[57];
    arr[59] += arr[58];
    arr[60] += arr[59];
    arr[61] += arr[60];
    arr[62] += arr[61];
    arr[63] += arr[62];
  }

  private final long[] tmp = new long[BLOCK_SIZE / 2];

  /**
   * Encode deltas of a strictly monotonically increasing sequence of integers. The provided {@code
   * longs} are expected to be deltas between consecutive values.
   */
  void encodeDeltas(long[] longs, DataOutput out) throws IOException {
    if (longs[0] == 1 && PForUtil.allEqual(longs)) { // happens with very dense postings
      out.writeByte((byte) 0);
    } else {
      long or = 0;
      for (long l : longs) {
        or |= l;
      }
      assert or != 0;
      final int bitsPerValue = PackedInts.bitsRequired(or);
      out.writeByte((byte) bitsPerValue);

      final int primitiveSize;
      if (bitsPerValue <= 4) {
        primitiveSize = 8;
        collapse8(longs);
      } else if (bitsPerValue <= 11) {
        primitiveSize = 16;
        collapse16(longs);
      } else {
        primitiveSize = 32;
        collapse32(longs);
      }
      encode(longs, bitsPerValue, primitiveSize, out, tmp);
    }
  }

  /** Decode deltas, compute the prefix sum and add {@code base} to all decoded longs. */
  void decodeAndPrefixSum(PostingDecodingUtil pdu, long base, long[] longs) throws IOException {
    final int bitsPerValue = Byte.toUnsignedInt(pdu.in.readByte());
    if (bitsPerValue == 0) {
      prefixSumOfOnes(longs, base);
    } else {
      decodeAndPrefixSum(bitsPerValue, pdu, base, longs);
    }
  }

  void skip(IndexInput in) throws IOException {
    final int bitsPerValue = Byte.toUnsignedInt(in.readByte());
    in.skipBytes(numBytes(bitsPerValue));
  }

  /** Delta-decode 128 integers into {@code longs}. */
  void decodeAndPrefixSum(int bitsPerValue, PostingDecodingUtil pdu, long base, long[] longs)
      throws IOException {
    switch (bitsPerValue) {
      case 1:
        decodeAndPrefixSum1(pdu, tmp, longs, base);
        break;
      case 2:
        decodeAndPrefixSum2(pdu, tmp, longs, base);
        break;
      case 3:
        decodeAndPrefixSum3(pdu, tmp, longs, base);
        break;
      case 4:
        decodeAndPrefixSum4(pdu, tmp, longs, base);
        break;
      case 5:
        decodeAndPrefixSum5(pdu, tmp, longs, base);
        break;
      case 6:
        decodeAndPrefixSum6(pdu, tmp, longs, base);
        break;
      case 7:
        decodeAndPrefixSum7(pdu, tmp, longs, base);
        break;
      case 8:
        decodeAndPrefixSum8(pdu, tmp, longs, base);
        break;
      case 9:
        decodeAndPrefixSum9(pdu, tmp, longs, base);
        break;
      case 10:
        decodeAndPrefixSum10(pdu, tmp, longs, base);
        break;
      case 11:
        decodeAndPrefixSum11(pdu, tmp, longs, base);
        break;
      case 12:
        decodeAndPrefixSum12(pdu, tmp, longs, base);
        break;
      case 13:
        decodeAndPrefixSum13(pdu, tmp, longs, base);
        break;
      case 14:
        decodeAndPrefixSum14(pdu, tmp, longs, base);
        break;
      case 15:
        decodeAndPrefixSum15(pdu, tmp, longs, base);
        break;
      case 16:
        decodeAndPrefixSum16(pdu, tmp, longs, base);
        break;
      case 17:
        decodeAndPrefixSum17(pdu, tmp, longs, base);
        break;
      case 18:
        decodeAndPrefixSum18(pdu, tmp, longs, base);
        break;
      case 19:
        decodeAndPrefixSum19(pdu, tmp, longs, base);
        break;
      case 20:
        decodeAndPrefixSum20(pdu, tmp, longs, base);
        break;
      case 21:
        decodeAndPrefixSum21(pdu, tmp, longs, base);
        break;
      case 22:
        decodeAndPrefixSum22(pdu, tmp, longs, base);
        break;
      case 23:
        decodeAndPrefixSum23(pdu, tmp, longs, base);
        break;
      case 24:
        decodeAndPrefixSum24(pdu, tmp, longs, base);
        break;
      default:
        decodeSlow(bitsPerValue, pdu, tmp, longs);
        prefixSum32(longs, base);
        break;
    }
  }

  private static void decodeAndPrefixSum1(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(2, longs, 7, 1, MASK8_1, longs, 14, MASK8_1);
    prefixSum8(longs, base);
  }

  private static void decodeAndPrefixSum2(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(4, longs, 6, 2, MASK8_2, longs, 12, MASK8_2);
    prefixSum8(longs, base);
  }

  private static void decodeAndPrefixSum3(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(6, longs, 5, 3, MASK8_3, tmp, 0, MASK8_2);
    decode3To8Remainder(tmp, longs);
    prefixSum8(longs, base);
  }

  private static void decode3To8Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 12; iter < 2; ++iter, tmpIdx += 3, longsIdx += 2) {
      long l0 = tmp[tmpIdx + 0] << 1;
      l0 |= (tmp[tmpIdx + 1] >>> 1) & MASK8_1;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 1] & MASK8_1) << 2;
      l1 |= tmp[tmpIdx + 2] << 0;
      longs[longsIdx + 1] = l1;
    }
  }

  private static void decodeAndPrefixSum4(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(8, longs, 4, 4, MASK8_4, longs, 8, MASK8_4);
    prefixSum8(longs, base);
  }

  private static void decodeAndPrefixSum5(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(10, longs, 11, 5, MASK16_5, tmp, 0, MASK16_1);
    decode5To16Remainder(tmp, longs);
    prefixSum16(longs, base);
  }

  private static void decode5To16Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 30; iter < 2; ++iter, tmpIdx += 5, longsIdx += 1) {
      long l0 = tmp[tmpIdx + 0] << 4;
      l0 |= tmp[tmpIdx + 1] << 3;
      l0 |= tmp[tmpIdx + 2] << 2;
      l0 |= tmp[tmpIdx + 3] << 1;
      l0 |= tmp[tmpIdx + 4] << 0;
      longs[longsIdx + 0] = l0;
    }
  }

  private static void decodeAndPrefixSum6(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(12, longs, 10, 6, MASK16_6, tmp, 0, MASK16_4);
    decode6To16Remainder(tmp, longs);
    prefixSum16(longs, base);
  }

  private static void decode6To16Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 24; iter < 4; ++iter, tmpIdx += 3, longsIdx += 2) {
      long l0 = tmp[tmpIdx + 0] << 2;
      l0 |= (tmp[tmpIdx + 1] >>> 2) & MASK16_2;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 1] & MASK16_2) << 4;
      l1 |= tmp[tmpIdx + 2] << 0;
      longs[longsIdx + 1] = l1;
    }
  }

  private static void decodeAndPrefixSum7(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(14, longs, 9, 7, MASK16_7, tmp, 0, MASK16_2);
    decode7To16Remainder(tmp, longs);
    prefixSum16(longs, base);
  }

  private static void decode7To16Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 28; iter < 2; ++iter, tmpIdx += 7, longsIdx += 2) {
      long l0 = tmp[tmpIdx + 0] << 5;
      l0 |= tmp[tmpIdx + 1] << 3;
      l0 |= tmp[tmpIdx + 2] << 1;
      l0 |= (tmp[tmpIdx + 3] >>> 1) & MASK16_1;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 3] & MASK16_1) << 6;
      l1 |= tmp[tmpIdx + 4] << 4;
      l1 |= tmp[tmpIdx + 5] << 2;
      l1 |= tmp[tmpIdx + 6] << 0;
      longs[longsIdx + 1] = l1;
    }
  }

  private static void decodeAndPrefixSum8(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(16, longs, 8, 8, MASK16_8, longs, 16, MASK16_8);
    prefixSum16(longs, base);
  }

  private static void decodeAndPrefixSum9(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(18, longs, 7, 9, MASK16_9, tmp, 0, MASK16_7);
    decode9To16Remainder(tmp, longs);
    prefixSum16(longs, base);
  }

  private static void decode9To16Remainder(long[] tmp, long[] longs) throws IOException {
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

  private static void decodeAndPrefixSum10(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(20, longs, 6, 10, MASK16_10, tmp, 0, MASK16_6);
    decode10To16Remainder(tmp, longs);
    prefixSum16(longs, base);
  }

  private static void decode10To16Remainder(long[] tmp, long[] longs) throws IOException {
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

  private static void decodeAndPrefixSum11(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(22, longs, 5, 11, MASK16_11, tmp, 0, MASK16_5);
    decode11To16Remainder(tmp, longs);
    prefixSum16(longs, base);
  }

  private static void decode11To16Remainder(long[] tmp, long[] longs) throws IOException {
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

  private static void decodeAndPrefixSum12(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(24, longs, 20, 12, MASK32_12, tmp, 0, MASK32_8);
    decode12To32Remainder(tmp, longs);
    prefixSum32(longs, base);
  }

  private static void decode12To32Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 48; iter < 8; ++iter, tmpIdx += 3, longsIdx += 2) {
      long l0 = tmp[tmpIdx + 0] << 4;
      l0 |= (tmp[tmpIdx + 1] >>> 4) & MASK32_4;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 1] & MASK32_4) << 8;
      l1 |= tmp[tmpIdx + 2] << 0;
      longs[longsIdx + 1] = l1;
    }
  }

  private static void decodeAndPrefixSum13(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(26, longs, 19, 13, MASK32_13, tmp, 0, MASK32_6);
    decode13To32Remainder(tmp, longs);
    prefixSum32(longs, base);
  }

  private static void decode13To32Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 52; iter < 2; ++iter, tmpIdx += 13, longsIdx += 6) {
      long l0 = tmp[tmpIdx + 0] << 7;
      l0 |= tmp[tmpIdx + 1] << 1;
      l0 |= (tmp[tmpIdx + 2] >>> 5) & MASK32_1;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 2] & MASK32_5) << 8;
      l1 |= tmp[tmpIdx + 3] << 2;
      l1 |= (tmp[tmpIdx + 4] >>> 4) & MASK32_2;
      longs[longsIdx + 1] = l1;
      long l2 = (tmp[tmpIdx + 4] & MASK32_4) << 9;
      l2 |= tmp[tmpIdx + 5] << 3;
      l2 |= (tmp[tmpIdx + 6] >>> 3) & MASK32_3;
      longs[longsIdx + 2] = l2;
      long l3 = (tmp[tmpIdx + 6] & MASK32_3) << 10;
      l3 |= tmp[tmpIdx + 7] << 4;
      l3 |= (tmp[tmpIdx + 8] >>> 2) & MASK32_4;
      longs[longsIdx + 3] = l3;
      long l4 = (tmp[tmpIdx + 8] & MASK32_2) << 11;
      l4 |= tmp[tmpIdx + 9] << 5;
      l4 |= (tmp[tmpIdx + 10] >>> 1) & MASK32_5;
      longs[longsIdx + 4] = l4;
      long l5 = (tmp[tmpIdx + 10] & MASK32_1) << 12;
      l5 |= tmp[tmpIdx + 11] << 6;
      l5 |= tmp[tmpIdx + 12] << 0;
      longs[longsIdx + 5] = l5;
    }
  }

  private static void decodeAndPrefixSum14(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(28, longs, 18, 14, MASK32_14, tmp, 0, MASK32_4);
    decode14To32Remainder(tmp, longs);
    prefixSum32(longs, base);
  }

  private static void decode14To32Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 56; iter < 4; ++iter, tmpIdx += 7, longsIdx += 2) {
      long l0 = tmp[tmpIdx + 0] << 10;
      l0 |= tmp[tmpIdx + 1] << 6;
      l0 |= tmp[tmpIdx + 2] << 2;
      l0 |= (tmp[tmpIdx + 3] >>> 2) & MASK32_2;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 3] & MASK32_2) << 12;
      l1 |= tmp[tmpIdx + 4] << 8;
      l1 |= tmp[tmpIdx + 5] << 4;
      l1 |= tmp[tmpIdx + 6] << 0;
      longs[longsIdx + 1] = l1;
    }
  }

  private static void decodeAndPrefixSum15(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(30, longs, 17, 15, MASK32_15, tmp, 0, MASK32_2);
    decode15To32Remainder(tmp, longs);
    prefixSum32(longs, base);
  }

  private static void decode15To32Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 60; iter < 2; ++iter, tmpIdx += 15, longsIdx += 2) {
      long l0 = tmp[tmpIdx + 0] << 13;
      l0 |= tmp[tmpIdx + 1] << 11;
      l0 |= tmp[tmpIdx + 2] << 9;
      l0 |= tmp[tmpIdx + 3] << 7;
      l0 |= tmp[tmpIdx + 4] << 5;
      l0 |= tmp[tmpIdx + 5] << 3;
      l0 |= tmp[tmpIdx + 6] << 1;
      l0 |= (tmp[tmpIdx + 7] >>> 1) & MASK32_1;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 7] & MASK32_1) << 14;
      l1 |= tmp[tmpIdx + 8] << 12;
      l1 |= tmp[tmpIdx + 9] << 10;
      l1 |= tmp[tmpIdx + 10] << 8;
      l1 |= tmp[tmpIdx + 11] << 6;
      l1 |= tmp[tmpIdx + 12] << 4;
      l1 |= tmp[tmpIdx + 13] << 2;
      l1 |= tmp[tmpIdx + 14] << 0;
      longs[longsIdx + 1] = l1;
    }
  }

  private static void decodeAndPrefixSum16(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(32, longs, 16, 16, MASK32_16, longs, 32, MASK32_16);
    prefixSum32(longs, base);
  }

  private static void decodeAndPrefixSum17(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(34, longs, 15, 17, MASK32_17, tmp, 0, MASK32_15);
    decode17To32Remainder(tmp, longs);
    prefixSum32(longs, base);
  }

  private static void decode17To32Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 34; iter < 2; ++iter, tmpIdx += 17, longsIdx += 15) {
      long l0 = tmp[tmpIdx + 0] << 2;
      l0 |= (tmp[tmpIdx + 1] >>> 13) & MASK32_2;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 1] & MASK32_13) << 4;
      l1 |= (tmp[tmpIdx + 2] >>> 11) & MASK32_4;
      longs[longsIdx + 1] = l1;
      long l2 = (tmp[tmpIdx + 2] & MASK32_11) << 6;
      l2 |= (tmp[tmpIdx + 3] >>> 9) & MASK32_6;
      longs[longsIdx + 2] = l2;
      long l3 = (tmp[tmpIdx + 3] & MASK32_9) << 8;
      l3 |= (tmp[tmpIdx + 4] >>> 7) & MASK32_8;
      longs[longsIdx + 3] = l3;
      long l4 = (tmp[tmpIdx + 4] & MASK32_7) << 10;
      l4 |= (tmp[tmpIdx + 5] >>> 5) & MASK32_10;
      longs[longsIdx + 4] = l4;
      long l5 = (tmp[tmpIdx + 5] & MASK32_5) << 12;
      l5 |= (tmp[tmpIdx + 6] >>> 3) & MASK32_12;
      longs[longsIdx + 5] = l5;
      long l6 = (tmp[tmpIdx + 6] & MASK32_3) << 14;
      l6 |= (tmp[tmpIdx + 7] >>> 1) & MASK32_14;
      longs[longsIdx + 6] = l6;
      long l7 = (tmp[tmpIdx + 7] & MASK32_1) << 16;
      l7 |= tmp[tmpIdx + 8] << 1;
      l7 |= (tmp[tmpIdx + 9] >>> 14) & MASK32_1;
      longs[longsIdx + 7] = l7;
      long l8 = (tmp[tmpIdx + 9] & MASK32_14) << 3;
      l8 |= (tmp[tmpIdx + 10] >>> 12) & MASK32_3;
      longs[longsIdx + 8] = l8;
      long l9 = (tmp[tmpIdx + 10] & MASK32_12) << 5;
      l9 |= (tmp[tmpIdx + 11] >>> 10) & MASK32_5;
      longs[longsIdx + 9] = l9;
      long l10 = (tmp[tmpIdx + 11] & MASK32_10) << 7;
      l10 |= (tmp[tmpIdx + 12] >>> 8) & MASK32_7;
      longs[longsIdx + 10] = l10;
      long l11 = (tmp[tmpIdx + 12] & MASK32_8) << 9;
      l11 |= (tmp[tmpIdx + 13] >>> 6) & MASK32_9;
      longs[longsIdx + 11] = l11;
      long l12 = (tmp[tmpIdx + 13] & MASK32_6) << 11;
      l12 |= (tmp[tmpIdx + 14] >>> 4) & MASK32_11;
      longs[longsIdx + 12] = l12;
      long l13 = (tmp[tmpIdx + 14] & MASK32_4) << 13;
      l13 |= (tmp[tmpIdx + 15] >>> 2) & MASK32_13;
      longs[longsIdx + 13] = l13;
      long l14 = (tmp[tmpIdx + 15] & MASK32_2) << 15;
      l14 |= tmp[tmpIdx + 16] << 0;
      longs[longsIdx + 14] = l14;
    }
  }

  private static void decodeAndPrefixSum18(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(36, longs, 14, 18, MASK32_18, tmp, 0, MASK32_14);
    decode18To32Remainder(tmp, longs);
    prefixSum32(longs, base);
  }

  private static void decode18To32Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 36; iter < 4; ++iter, tmpIdx += 9, longsIdx += 7) {
      long l0 = tmp[tmpIdx + 0] << 4;
      l0 |= (tmp[tmpIdx + 1] >>> 10) & MASK32_4;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 1] & MASK32_10) << 8;
      l1 |= (tmp[tmpIdx + 2] >>> 6) & MASK32_8;
      longs[longsIdx + 1] = l1;
      long l2 = (tmp[tmpIdx + 2] & MASK32_6) << 12;
      l2 |= (tmp[tmpIdx + 3] >>> 2) & MASK32_12;
      longs[longsIdx + 2] = l2;
      long l3 = (tmp[tmpIdx + 3] & MASK32_2) << 16;
      l3 |= tmp[tmpIdx + 4] << 2;
      l3 |= (tmp[tmpIdx + 5] >>> 12) & MASK32_2;
      longs[longsIdx + 3] = l3;
      long l4 = (tmp[tmpIdx + 5] & MASK32_12) << 6;
      l4 |= (tmp[tmpIdx + 6] >>> 8) & MASK32_6;
      longs[longsIdx + 4] = l4;
      long l5 = (tmp[tmpIdx + 6] & MASK32_8) << 10;
      l5 |= (tmp[tmpIdx + 7] >>> 4) & MASK32_10;
      longs[longsIdx + 5] = l5;
      long l6 = (tmp[tmpIdx + 7] & MASK32_4) << 14;
      l6 |= tmp[tmpIdx + 8] << 0;
      longs[longsIdx + 6] = l6;
    }
  }

  private static void decodeAndPrefixSum19(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(38, longs, 13, 19, MASK32_19, tmp, 0, MASK32_13);
    decode19To32Remainder(tmp, longs);
    prefixSum32(longs, base);
  }

  private static void decode19To32Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 38; iter < 2; ++iter, tmpIdx += 19, longsIdx += 13) {
      long l0 = tmp[tmpIdx + 0] << 6;
      l0 |= (tmp[tmpIdx + 1] >>> 7) & MASK32_6;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 1] & MASK32_7) << 12;
      l1 |= (tmp[tmpIdx + 2] >>> 1) & MASK32_12;
      longs[longsIdx + 1] = l1;
      long l2 = (tmp[tmpIdx + 2] & MASK32_1) << 18;
      l2 |= tmp[tmpIdx + 3] << 5;
      l2 |= (tmp[tmpIdx + 4] >>> 8) & MASK32_5;
      longs[longsIdx + 2] = l2;
      long l3 = (tmp[tmpIdx + 4] & MASK32_8) << 11;
      l3 |= (tmp[tmpIdx + 5] >>> 2) & MASK32_11;
      longs[longsIdx + 3] = l3;
      long l4 = (tmp[tmpIdx + 5] & MASK32_2) << 17;
      l4 |= tmp[tmpIdx + 6] << 4;
      l4 |= (tmp[tmpIdx + 7] >>> 9) & MASK32_4;
      longs[longsIdx + 4] = l4;
      long l5 = (tmp[tmpIdx + 7] & MASK32_9) << 10;
      l5 |= (tmp[tmpIdx + 8] >>> 3) & MASK32_10;
      longs[longsIdx + 5] = l5;
      long l6 = (tmp[tmpIdx + 8] & MASK32_3) << 16;
      l6 |= tmp[tmpIdx + 9] << 3;
      l6 |= (tmp[tmpIdx + 10] >>> 10) & MASK32_3;
      longs[longsIdx + 6] = l6;
      long l7 = (tmp[tmpIdx + 10] & MASK32_10) << 9;
      l7 |= (tmp[tmpIdx + 11] >>> 4) & MASK32_9;
      longs[longsIdx + 7] = l7;
      long l8 = (tmp[tmpIdx + 11] & MASK32_4) << 15;
      l8 |= tmp[tmpIdx + 12] << 2;
      l8 |= (tmp[tmpIdx + 13] >>> 11) & MASK32_2;
      longs[longsIdx + 8] = l8;
      long l9 = (tmp[tmpIdx + 13] & MASK32_11) << 8;
      l9 |= (tmp[tmpIdx + 14] >>> 5) & MASK32_8;
      longs[longsIdx + 9] = l9;
      long l10 = (tmp[tmpIdx + 14] & MASK32_5) << 14;
      l10 |= tmp[tmpIdx + 15] << 1;
      l10 |= (tmp[tmpIdx + 16] >>> 12) & MASK32_1;
      longs[longsIdx + 10] = l10;
      long l11 = (tmp[tmpIdx + 16] & MASK32_12) << 7;
      l11 |= (tmp[tmpIdx + 17] >>> 6) & MASK32_7;
      longs[longsIdx + 11] = l11;
      long l12 = (tmp[tmpIdx + 17] & MASK32_6) << 13;
      l12 |= tmp[tmpIdx + 18] << 0;
      longs[longsIdx + 12] = l12;
    }
  }

  private static void decodeAndPrefixSum20(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(40, longs, 12, 20, MASK32_20, tmp, 0, MASK32_12);
    decode20To32Remainder(tmp, longs);
    prefixSum32(longs, base);
  }

  private static void decode20To32Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 40; iter < 8; ++iter, tmpIdx += 5, longsIdx += 3) {
      long l0 = tmp[tmpIdx + 0] << 8;
      l0 |= (tmp[tmpIdx + 1] >>> 4) & MASK32_8;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 1] & MASK32_4) << 16;
      l1 |= tmp[tmpIdx + 2] << 4;
      l1 |= (tmp[tmpIdx + 3] >>> 8) & MASK32_4;
      longs[longsIdx + 1] = l1;
      long l2 = (tmp[tmpIdx + 3] & MASK32_8) << 12;
      l2 |= tmp[tmpIdx + 4] << 0;
      longs[longsIdx + 2] = l2;
    }
  }

  private static void decodeAndPrefixSum21(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(42, longs, 11, 21, MASK32_21, tmp, 0, MASK32_11);
    decode21To32Remainder(tmp, longs);
    prefixSum32(longs, base);
  }

  private static void decode21To32Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 42; iter < 2; ++iter, tmpIdx += 21, longsIdx += 11) {
      long l0 = tmp[tmpIdx + 0] << 10;
      l0 |= (tmp[tmpIdx + 1] >>> 1) & MASK32_10;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 1] & MASK32_1) << 20;
      l1 |= tmp[tmpIdx + 2] << 9;
      l1 |= (tmp[tmpIdx + 3] >>> 2) & MASK32_9;
      longs[longsIdx + 1] = l1;
      long l2 = (tmp[tmpIdx + 3] & MASK32_2) << 19;
      l2 |= tmp[tmpIdx + 4] << 8;
      l2 |= (tmp[tmpIdx + 5] >>> 3) & MASK32_8;
      longs[longsIdx + 2] = l2;
      long l3 = (tmp[tmpIdx + 5] & MASK32_3) << 18;
      l3 |= tmp[tmpIdx + 6] << 7;
      l3 |= (tmp[tmpIdx + 7] >>> 4) & MASK32_7;
      longs[longsIdx + 3] = l3;
      long l4 = (tmp[tmpIdx + 7] & MASK32_4) << 17;
      l4 |= tmp[tmpIdx + 8] << 6;
      l4 |= (tmp[tmpIdx + 9] >>> 5) & MASK32_6;
      longs[longsIdx + 4] = l4;
      long l5 = (tmp[tmpIdx + 9] & MASK32_5) << 16;
      l5 |= tmp[tmpIdx + 10] << 5;
      l5 |= (tmp[tmpIdx + 11] >>> 6) & MASK32_5;
      longs[longsIdx + 5] = l5;
      long l6 = (tmp[tmpIdx + 11] & MASK32_6) << 15;
      l6 |= tmp[tmpIdx + 12] << 4;
      l6 |= (tmp[tmpIdx + 13] >>> 7) & MASK32_4;
      longs[longsIdx + 6] = l6;
      long l7 = (tmp[tmpIdx + 13] & MASK32_7) << 14;
      l7 |= tmp[tmpIdx + 14] << 3;
      l7 |= (tmp[tmpIdx + 15] >>> 8) & MASK32_3;
      longs[longsIdx + 7] = l7;
      long l8 = (tmp[tmpIdx + 15] & MASK32_8) << 13;
      l8 |= tmp[tmpIdx + 16] << 2;
      l8 |= (tmp[tmpIdx + 17] >>> 9) & MASK32_2;
      longs[longsIdx + 8] = l8;
      long l9 = (tmp[tmpIdx + 17] & MASK32_9) << 12;
      l9 |= tmp[tmpIdx + 18] << 1;
      l9 |= (tmp[tmpIdx + 19] >>> 10) & MASK32_1;
      longs[longsIdx + 9] = l9;
      long l10 = (tmp[tmpIdx + 19] & MASK32_10) << 11;
      l10 |= tmp[tmpIdx + 20] << 0;
      longs[longsIdx + 10] = l10;
    }
  }

  private static void decodeAndPrefixSum22(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(44, longs, 10, 22, MASK32_22, tmp, 0, MASK32_10);
    decode22To32Remainder(tmp, longs);
    prefixSum32(longs, base);
  }

  private static void decode22To32Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 44; iter < 4; ++iter, tmpIdx += 11, longsIdx += 5) {
      long l0 = tmp[tmpIdx + 0] << 12;
      l0 |= tmp[tmpIdx + 1] << 2;
      l0 |= (tmp[tmpIdx + 2] >>> 8) & MASK32_2;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 2] & MASK32_8) << 14;
      l1 |= tmp[tmpIdx + 3] << 4;
      l1 |= (tmp[tmpIdx + 4] >>> 6) & MASK32_4;
      longs[longsIdx + 1] = l1;
      long l2 = (tmp[tmpIdx + 4] & MASK32_6) << 16;
      l2 |= tmp[tmpIdx + 5] << 6;
      l2 |= (tmp[tmpIdx + 6] >>> 4) & MASK32_6;
      longs[longsIdx + 2] = l2;
      long l3 = (tmp[tmpIdx + 6] & MASK32_4) << 18;
      l3 |= tmp[tmpIdx + 7] << 8;
      l3 |= (tmp[tmpIdx + 8] >>> 2) & MASK32_8;
      longs[longsIdx + 3] = l3;
      long l4 = (tmp[tmpIdx + 8] & MASK32_2) << 20;
      l4 |= tmp[tmpIdx + 9] << 10;
      l4 |= tmp[tmpIdx + 10] << 0;
      longs[longsIdx + 4] = l4;
    }
  }

  private static void decodeAndPrefixSum23(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(46, longs, 9, 23, MASK32_23, tmp, 0, MASK32_9);
    decode23To32Remainder(tmp, longs);
    prefixSum32(longs, base);
  }

  private static void decode23To32Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 46; iter < 2; ++iter, tmpIdx += 23, longsIdx += 9) {
      long l0 = tmp[tmpIdx + 0] << 14;
      l0 |= tmp[tmpIdx + 1] << 5;
      l0 |= (tmp[tmpIdx + 2] >>> 4) & MASK32_5;
      longs[longsIdx + 0] = l0;
      long l1 = (tmp[tmpIdx + 2] & MASK32_4) << 19;
      l1 |= tmp[tmpIdx + 3] << 10;
      l1 |= tmp[tmpIdx + 4] << 1;
      l1 |= (tmp[tmpIdx + 5] >>> 8) & MASK32_1;
      longs[longsIdx + 1] = l1;
      long l2 = (tmp[tmpIdx + 5] & MASK32_8) << 15;
      l2 |= tmp[tmpIdx + 6] << 6;
      l2 |= (tmp[tmpIdx + 7] >>> 3) & MASK32_6;
      longs[longsIdx + 2] = l2;
      long l3 = (tmp[tmpIdx + 7] & MASK32_3) << 20;
      l3 |= tmp[tmpIdx + 8] << 11;
      l3 |= tmp[tmpIdx + 9] << 2;
      l3 |= (tmp[tmpIdx + 10] >>> 7) & MASK32_2;
      longs[longsIdx + 3] = l3;
      long l4 = (tmp[tmpIdx + 10] & MASK32_7) << 16;
      l4 |= tmp[tmpIdx + 11] << 7;
      l4 |= (tmp[tmpIdx + 12] >>> 2) & MASK32_7;
      longs[longsIdx + 4] = l4;
      long l5 = (tmp[tmpIdx + 12] & MASK32_2) << 21;
      l5 |= tmp[tmpIdx + 13] << 12;
      l5 |= tmp[tmpIdx + 14] << 3;
      l5 |= (tmp[tmpIdx + 15] >>> 6) & MASK32_3;
      longs[longsIdx + 5] = l5;
      long l6 = (tmp[tmpIdx + 15] & MASK32_6) << 17;
      l6 |= tmp[tmpIdx + 16] << 8;
      l6 |= (tmp[tmpIdx + 17] >>> 1) & MASK32_8;
      longs[longsIdx + 6] = l6;
      long l7 = (tmp[tmpIdx + 17] & MASK32_1) << 22;
      l7 |= tmp[tmpIdx + 18] << 13;
      l7 |= tmp[tmpIdx + 19] << 4;
      l7 |= (tmp[tmpIdx + 20] >>> 5) & MASK32_4;
      longs[longsIdx + 7] = l7;
      long l8 = (tmp[tmpIdx + 20] & MASK32_5) << 18;
      l8 |= tmp[tmpIdx + 21] << 9;
      l8 |= tmp[tmpIdx + 22] << 0;
      longs[longsIdx + 8] = l8;
    }
  }

  private static void decodeAndPrefixSum24(
      PostingDecodingUtil pdu, long[] tmp, long[] longs, long base) throws IOException {
    pdu.splitLongs(48, longs, 8, 24, MASK32_24, tmp, 0, MASK32_8);
    decode24To32Remainder(tmp, longs);
    prefixSum32(longs, base);
  }

  private static void decode24To32Remainder(long[] tmp, long[] longs) throws IOException {
    for (int iter = 0, tmpIdx = 0, longsIdx = 48; iter < 16; ++iter, tmpIdx += 3, longsIdx += 1) {
      long l0 = tmp[tmpIdx + 0] << 16;
      l0 |= tmp[tmpIdx + 1] << 8;
      l0 |= tmp[tmpIdx + 2] << 0;
      longs[longsIdx + 0] = l0;
    }
  }
}
