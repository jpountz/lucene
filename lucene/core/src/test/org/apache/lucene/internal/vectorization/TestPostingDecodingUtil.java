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

import org.apache.lucene.codecs.lucene912.ForUtil;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestPostingDecodingUtil extends LuceneTestCase {

  public void testDuelSplitLongs() throws Exception {
    final int iterations = atLeast(100);

    try (Directory dir = new MMapDirectory(createTempDir())) {
      try (IndexOutput out = dir.createOutput("tests.bin", IOContext.DEFAULT)) {
        out.writeInt(random().nextInt());
        for (int i = 0; i < ForUtil.BLOCK_SIZE; ++i) {
          out.writeLong(random().nextInt());
        }
      }
      VectorizationProvider vectorizationProvider = VectorizationProvider.lookup(true);
      try (IndexInput in = dir.openInput("tests.bin", IOContext.DEFAULT)) {
        long[] expectedB = new long[ForUtil.BLOCK_SIZE];
        long[] expectedC = new long[ForUtil.BLOCK_SIZE];
        long[] actualB = new long[ForUtil.BLOCK_SIZE];
        long[] actualC = new long[ForUtil.BLOCK_SIZE];
        for (int iter = 0; iter < iterations; ++iter) {
          // Initialize arrays with random content.
          for (int i = 0; i < expectedB.length; ++i) {
            expectedB[i] = random().nextLong();
            actualB[i] = expectedB[i];
            expectedC[i] = random().nextLong();
            actualC[i] = expectedC[i];
          }
          int bShift1 = TestUtil.nextInt(random(), 1, 31);
          int bShift2 = TestUtil.nextInt(random(), 1, 31);
          int bShift3 = TestUtil.nextInt(random(), 1, 31);
          int count = TestUtil.nextInt(random(), 1, 64);
          long bMask = random().nextLong();
          int cIndex = random().nextInt(64);
          long cMask = random().nextLong();
          long startFP = random().nextInt(4);

          // Work on a slice that has just the right number of bytes to make the test fail with an
          // index-out-of-bounds in case the implementation reads more than the allowed number of
          // padding bytes.
          IndexInput slice = in.slice("test", 0, startFP + count * Long.BYTES);

          PostingDecodingUtil defaultUtil = new PostingDecodingUtil(slice);
          PostingDecodingUtil optimizedUtil = vectorizationProvider.newPostingDecodingUtil(slice);

          slice.seek(startFP);
          defaultUtil.splitLongs1(count, expectedB, bShift1, bMask, expectedC, cIndex, cMask);
          long expectedEndFP = slice.getFilePointer();
          slice.seek(startFP);
          optimizedUtil.splitLongs1(count, actualB, bShift1, bMask, actualC, cIndex, cMask);
          assertEquals(expectedEndFP, slice.getFilePointer());
          assertArrayEquals(expectedB, actualB);
          assertArrayEquals(expectedC, actualC);

          slice.seek(startFP);
          defaultUtil.splitLongs2(
              count, expectedB, bShift1, bShift2, bMask, expectedC, cIndex, cMask);
          expectedEndFP = slice.getFilePointer();
          slice.seek(startFP);
          optimizedUtil.splitLongs2(
              count, actualB, bShift1, bShift2, bMask, actualC, cIndex, cMask);
          assertEquals(expectedEndFP, slice.getFilePointer());
          assertArrayEquals(expectedB, actualB);
          assertArrayEquals(expectedC, actualC);

          slice.seek(startFP);
          defaultUtil.splitLongs3(
              count, expectedB, bShift1, bShift2, bShift3, bMask, expectedC, cIndex, cMask);
          expectedEndFP = slice.getFilePointer();
          slice.seek(startFP);
          optimizedUtil.splitLongs3(
              count, actualB, bShift1, bShift2, bShift3, bMask, actualC, cIndex, cMask);
          assertEquals(expectedEndFP, slice.getFilePointer());
          assertArrayEquals(expectedB, actualB);
          assertArrayEquals(expectedC, actualC);
        }
      }
    }
  }
}
