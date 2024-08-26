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
package org.apache.lucene.internal.vectorization;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteOrder;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.apache.lucene.store.IndexInput;

final class MemorySegmentPostingDecodingUtil extends PostingDecodingUtil {

  private static final VectorSpecies<Long> LONG_SPECIES =
      PanamaVectorConstants.PRERERRED_LONG_SPECIES;

  private final MemorySegment memorySegment;

  MemorySegmentPostingDecodingUtil(IndexInput in, MemorySegment memorySegment) {
    super(in);
    this.memorySegment = memorySegment;
  }

  @Override
  public void splitLongs1(
      int count, long[] b, int bShift, long bMask, long[] c, int cIndex, long cMask)
      throws IOException {
    if (count < LONG_SPECIES.length()) {
      // Not enough data to vectorize without going out-of-bounds. In practice, this branch is never
      // used if the bit width is 256, and is used for 2 and 3 bits per value if the bit width is
      // 512.
      super.splitLongs1(count, b, bShift, bMask, c, cIndex, cMask);
      return;
    }

    long offset = in.getFilePointer();
    long endOffset = offset + count * Long.BYTES;

    LongVector bShiftVector = LongVector.broadcast(LONG_SPECIES, bShift);
    LongVector bMaskVector = LongVector.broadcast(LONG_SPECIES, bMask);
    LongVector cMaskVector = LongVector.broadcast(LONG_SPECIES, cMask);
    
    int loopBound = LONG_SPECIES.loopBound(count - 1);
    for (int i = 0;
        i < loopBound;
        i += LONG_SPECIES.length(), offset += LONG_SPECIES.length() * Long.BYTES) {
      LongVector vector =
          LongVector.fromMemorySegment(
              LONG_SPECIES, memorySegment, offset, ByteOrder.LITTLE_ENDIAN);
      vector
          .lanewise(VectorOperators.LSHR, bShiftVector)
          .lanewise(VectorOperators.AND, bMaskVector)
          .intoArray(b, i);
      vector.lanewise(VectorOperators.AND, cMaskVector).intoArray(c, cIndex + i);
    }

    int i = count - LONG_SPECIES.length();
    LongVector vector =
        LongVector.fromMemorySegment(
            LONG_SPECIES,
            memorySegment,
            endOffset - LONG_SPECIES.length() * Long.BYTES,
            ByteOrder.LITTLE_ENDIAN);
    vector
        .lanewise(VectorOperators.LSHR, bShiftVector)
        .lanewise(VectorOperators.AND, bMaskVector)
        .intoArray(b, i);
    vector.lanewise(VectorOperators.AND, cMaskVector).intoArray(c, cIndex + i);

    in.seek(endOffset);
  }

  @Override
  public void splitLongs2(
      int count, long[] b, int bShift1, int bShift2, long bMask, long[] c, int cIndex, long cMask)
      throws IOException {
    if (count < LONG_SPECIES.length()) {
      // Not enough data to vectorize without going out-of-bounds. In practice, this branch is never
      // used if the bit width is 256, and is used for 2 and 3 bits per value if the bit width is
      // 512.
      super.splitLongs2(count, b, bShift1, bShift2, bMask, c, cIndex, cMask);
      return;
    }

    long offset = in.getFilePointer();
    long endOffset = offset + count * Long.BYTES;

    LongVector bShift1Vector = LongVector.broadcast(LONG_SPECIES, bShift1);
    LongVector bShift2Vector = LongVector.broadcast(LONG_SPECIES, bShift2);
    LongVector bMaskVector = LongVector.broadcast(LONG_SPECIES, bMask);
    LongVector cMaskVector = LongVector.broadcast(LONG_SPECIES, cMask);
    
    int loopBound = LONG_SPECIES.loopBound(count - 1);
    for (int i = 0;
        i < loopBound;
        i += LONG_SPECIES.length(), offset += LONG_SPECIES.length() * Long.BYTES) {
      LongVector vector =
          LongVector.fromMemorySegment(
              LONG_SPECIES, memorySegment, offset, ByteOrder.LITTLE_ENDIAN);
      vector
          .lanewise(VectorOperators.LSHR, bShift1Vector)
          .lanewise(VectorOperators.AND, bMaskVector)
          .intoArray(b, i);
      vector
          .lanewise(VectorOperators.LSHR, bShift2Vector)
          .lanewise(VectorOperators.AND, bMaskVector)
          .intoArray(b, count + i);
      vector.lanewise(VectorOperators.AND, cMaskVector).intoArray(c, cIndex + i);
    }

    int i = count - LONG_SPECIES.length();
    LongVector vector =
        LongVector.fromMemorySegment(
            LONG_SPECIES,
            memorySegment,
            endOffset - LONG_SPECIES.length() * Long.BYTES,
            ByteOrder.LITTLE_ENDIAN);
    vector
        .lanewise(VectorOperators.LSHR, bShift1Vector)
        .lanewise(VectorOperators.AND, bMaskVector)
        .intoArray(b, i);
    vector
        .lanewise(VectorOperators.LSHR, bShift2Vector)
        .lanewise(VectorOperators.AND, bMaskVector)
        .intoArray(b, count + i);
    vector.lanewise(VectorOperators.AND, cMaskVector).intoArray(c, cIndex + i);

    in.seek(endOffset);
  }

  @Override
  public void splitLongs3(
      int count,
      long[] b,
      int bShift1,
      int bShift2,
      int bShift3,
      long bMask,
      long[] c,
      int cIndex,
      long cMask)
      throws IOException {
    if (count < LONG_SPECIES.length()) {
      // Not enough data to vectorize without going out-of-bounds. In practice, this branch is never
      // used if the bit width is 256, and is used for 2 and 3 bits per value if the bit width is
      // 512.
      super.splitLongs3(count, b, bShift1, bShift2, bShift3, bMask, c, cIndex, cMask);
      return;
    }

    long offset = in.getFilePointer();
    long endOffset = offset + count * Long.BYTES;

    LongVector bShift1Vector = LongVector.broadcast(LONG_SPECIES, bShift1);
    LongVector bShift2Vector = LongVector.broadcast(LONG_SPECIES, bShift2);
    LongVector bShift3Vector = LongVector.broadcast(LONG_SPECIES, bShift3);
    LongVector bMaskVector = LongVector.broadcast(LONG_SPECIES, bMask);
    LongVector cMaskVector = LongVector.broadcast(LONG_SPECIES, cMask);
    
    int loopBound = LONG_SPECIES.loopBound(count - 1);
    for (int i = 0;
        i < loopBound;
        i += LONG_SPECIES.length(), offset += LONG_SPECIES.length() * Long.BYTES) {
      LongVector vector =
          LongVector.fromMemorySegment(
              LONG_SPECIES, memorySegment, offset, ByteOrder.LITTLE_ENDIAN);
      vector
          .lanewise(VectorOperators.LSHR, bShift1Vector)
          .lanewise(VectorOperators.AND, bMaskVector)
          .intoArray(b, i);
      vector
          .lanewise(VectorOperators.LSHR, bShift2Vector)
          .lanewise(VectorOperators.AND, bMaskVector)
          .intoArray(b, count + i);
      vector
          .lanewise(VectorOperators.LSHR, bShift3Vector)
          .lanewise(VectorOperators.AND, bMaskVector)
          .intoArray(b, 2 * count + i);
      vector.lanewise(VectorOperators.AND, cMaskVector).intoArray(c, cIndex + i);
    }

    int i = count - LONG_SPECIES.length();
    LongVector vector =
        LongVector.fromMemorySegment(
            LONG_SPECIES,
            memorySegment,
            endOffset - LONG_SPECIES.length() * Long.BYTES,
            ByteOrder.LITTLE_ENDIAN);
    vector
        .lanewise(VectorOperators.LSHR, bShift1Vector)
        .lanewise(VectorOperators.AND, bMaskVector)
        .intoArray(b, i);
    vector
        .lanewise(VectorOperators.LSHR, bShift2Vector)
        .lanewise(VectorOperators.AND, bMaskVector)
        .intoArray(b, count + i);
    vector
        .lanewise(VectorOperators.LSHR, bShift3Vector)
        .lanewise(VectorOperators.AND, bMaskVector)
        .intoArray(b, 2 * count + i);
    vector.lanewise(VectorOperators.AND, cMaskVector).intoArray(c, cIndex + i);

    in.seek(endOffset);
  }
}
