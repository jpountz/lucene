#! /usr/bin/env python

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from math import gcd

"""Code generation for ForUtil.java"""

MAX_SPECIALIZED_BITS_PER_VALUE = 16
OUTPUT_FILE = "ForUtil.java"
PRIMITIVE_SIZE = [8, 16, 32]
HEADER = """// This file has been automatically generated, DO NOT EDIT

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
import org.apache.lucene.store.IndexInput;

/**
 * Inspired from https://fulmicoton.com/posts/bitpacking/
 * Encodes multiple integers in a long to get SIMD-like speedups.
 * If bitsPerValue &lt;= 8 then we pack 8 ints per long
 * else if bitsPerValue &lt;= 16 we pack 4 ints per long
 * else we pack 2 ints per long
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

  static void encode(long[] longs, int bitsPerValue, int primitiveSize, DataOutput out, long[] tmp) throws IOException {
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

"""

def writeRemainder(bpv, next_primitive, remaining_bits_per_long, o, num_values, f):
  iteration = 1
  num_longs = bpv * num_values / remaining_bits_per_long
  while num_longs % 2 == 0 and num_values % 2 == 0:
    num_longs /= 2
    num_values /= 2
    iteration *= 2
  f.write('    for (int iter = 0, tmpIdx = 0, longsIdx = %d; iter < %d; ++iter, tmpIdx += %d, longsIdx += %d) {\n' %(o, iteration, num_longs, num_values))
  i = 0
  remaining_bits = 0
  tmp_idx = 0
  for i in range(int(num_values)):
    b = bpv
    if remaining_bits == 0:
      b -= remaining_bits_per_long
      f.write('      long l%d = tmp[tmpIdx + %d] << %d;\n' %(i, tmp_idx, b))
    else:
      b -= remaining_bits
      f.write('      long l%d = (tmp[tmpIdx + %d] & MASK%d_%d) << %d;\n' %(i, tmp_idx, next_primitive, remaining_bits, b))
    tmp_idx += 1
    while b >= remaining_bits_per_long:
      b -= remaining_bits_per_long
      f.write('      l%d |= tmp[tmpIdx + %d] << %d;\n' %(i, tmp_idx, b))
      tmp_idx += 1
    if b > 0:
      f.write('      l%d |= (tmp[tmpIdx + %d] >>> %d) & MASK%d_%d;\n' %(i, tmp_idx, remaining_bits_per_long-b, next_primitive, b))
      remaining_bits = remaining_bits_per_long-b
    f.write('      longs[longsIdx + %d] = l%d;\n' %(i, i))
  f.write('    }\n')
  
def writeDecode(bpv, f):
  primitive_size = 32
  if bpv <= 8:
    primitive_size = 8
  elif bpv <= 16:
    primitive_size = 16
  f.write('  private static void decode%d(PostingDecodingUtil pdu, long[] tmp, long[] longs) throws IOException {\n' %bpv)

  remaining_bits = primitive_size % bpv
  if bpv == primitive_size:
    f.write('    pdu.in.readLongs(longs, 0, %d);\n' %(bpv*2))
  else:
    num_values_per_long = 64 / primitive_size
    remaining_bits = primitive_size % bpv
    num_shifts = (primitive_size - 1) // bpv
    o = 2 * bpv * num_shifts

    if remaining_bits == 0:
      if num_shifts == 1:
        f.write('    pdu.splitLongs1(%d, longs, %d, MASK%d_%d, longs, %d, MASK%d_%d);\n' %(bpv*2, primitive_size - bpv, primitive_size, bpv, o, primitive_size, primitive_size - num_shifts * bpv))
      elif num_shifts == 2:
        f.write('    pdu.splitLongs2(%d, longs, %d, %d, MASK%d_%d, longs, %d, MASK%d_%d);\n' %(bpv*2, primitive_size - bpv, primitive_size - 2 * bpv, primitive_size, bpv, o, primitive_size, primitive_size - num_shifts * bpv))
      elif num_shifts == 3:
        f.write('    pdu.splitLongs3(%d, longs, %d, %d, %d, MASK%d_%d, longs, %d, MASK%d_%d);\n' %(bpv*2, primitive_size - bpv, primitive_size - 2 * bpv, primitive_size - 3 * bpv, primitive_size, bpv, o, primitive_size, primitive_size - num_shifts * bpv))
      else:
        f.write('    pdu.in.readLongs(tmp, 0, %d);\n' %(bpv * 2))
        f.write('    for (int i = 0; i < %d; ++i) {\n' %(bpv*2))
        for shift in range(num_shifts+1):
          f.write('      longs[%d + i] = (tmp[i] >>> %d) & MASK%d_%d;\n' %(shift * bpv * 2, primitive_size - (shift + 1) * bpv, primitive_size, bpv))
        f.write('    }\n')

    else:
      if num_shifts == 1:
        f.write('    pdu.splitLongs1(%d, longs, %d, MASK%d_%d, tmp, 0, MASK%d_%d);\n' %(bpv*2, primitive_size - bpv, primitive_size, bpv, primitive_size, primitive_size - num_shifts * bpv))
      elif num_shifts == 2:
        f.write('    pdu.splitLongs2(%d, longs, %d, %d, MASK%d_%d, tmp, 0, MASK%d_%d);\n' %(bpv*2, primitive_size - bpv, primitive_size - 2 * bpv, primitive_size, bpv, primitive_size, primitive_size - num_shifts * bpv))
      else:
        f.write('    pdu.splitLongs3(%d, longs, %d, %d, %d, MASK%d_%d, tmp, 0, MASK%d_%d);\n' %(bpv*2, primitive_size - bpv, primitive_size - 2 * bpv, primitive_size - 3 * bpv, primitive_size, bpv, primitive_size, primitive_size - num_shifts * bpv))
      f.write('    decode%dTo%dRemainder(tmp, longs);' %(bpv, primitive_size))
  f.write('    expand%d(longs);\n' %primitive_size)
  f.write('  }\n')

  if remaining_bits != 0:
    f.write('\n')
    f.write('  static void decode%dTo%dRemainder(long[] tmp, long[] longs) throws IOException {\n' %(bpv, primitive_size))
    writeRemainder(bpv, primitive_size, remaining_bits, o, 128/num_values_per_long - o, f)
    f.write('  }\n')

if __name__ == '__main__':
  f = open(OUTPUT_FILE, 'w')
  f.write(HEADER)
  for primitive_size in PRIMITIVE_SIZE:
    f.write('  static final long[] MASKS%d = new long[%d];\n' %(primitive_size, primitive_size))
  f.write('\n')
  f.write('  static {\n')
  for primitive_size in PRIMITIVE_SIZE:
    f.write('    for (int i = 0; i < %d; ++i) {\n' %primitive_size)
    f.write('      MASKS%d[i] = mask%d(i);\n' %(primitive_size, primitive_size))
    f.write('    }\n')
  f.write('  }')
  f.write("""
  // mark values in array as final longs to avoid the cost of reading array, arrays should only be
  // used when the idx is a variable
""")
  for primitive_size in PRIMITIVE_SIZE:
    for bpv in range(1, min(MAX_SPECIALIZED_BITS_PER_VALUE + 1, primitive_size)):
      f.write('  static final long MASK%d_%d = MASKS%d[%d];\n' %(primitive_size, bpv, primitive_size, bpv))

  f.write("""
  @FunctionalInterface
  private interface Decoder {
    void decode(PostingDecodingUtil pdu, long[] tmp, long[] longs) throws IOException;
  }

  private static Decoder[] DECODERS = new Decoder[%d];
  static {
""" %(MAX_SPECIALIZED_BITS_PER_VALUE + 1))

  for bpv in range(1, MAX_SPECIALIZED_BITS_PER_VALUE + 1):
    f.write("    DECODERS[%d] = ForUtil::decode%d;\n" %(bpv, bpv))
  f.write("""
  }
""")

  f.write("""
  /** Decode 128 integers into {@code longs}. */
  void decode(int bitsPerValue, PostingDecodingUtil pdu, long[] longs) throws IOException {
    if (bitsPerValue <= %d) {
      DECODERS[bitsPerValue].decode(pdu, tmp, longs);
    } else {
      decodeSlow(bitsPerValue, pdu, tmp, longs);
      expand32(longs);
    }
  }
""" %MAX_SPECIALIZED_BITS_PER_VALUE)

  for i in range(1, MAX_SPECIALIZED_BITS_PER_VALUE+1):
    writeDecode(i, f)
    if i < MAX_SPECIALIZED_BITS_PER_VALUE:
      f.write('\n')

  f.write('}\n')
