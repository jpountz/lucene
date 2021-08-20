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
package org.apache.lucene.codecs.lucene90;


import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.LuceneTestCase;

import java.io.IOException;

public class TestDeltaLZ4DocValuesEncoder extends LuceneTestCase {
    public void testRandomValues() throws IOException {
        long[] arr = new long[DeltaLZ4DocValuesEncoder.BLOCK_SIZE];
        for (int i = 0; i < DeltaLZ4DocValuesEncoder.BLOCK_SIZE; ++i) {
            arr[i] = random().nextLong();
        }
        doTest(arr, -1);
    }

    public void testDeltaOverflowValues() throws IOException {
        long[] arr = new long[DeltaLZ4DocValuesEncoder.BLOCK_SIZE];
        for (int i = 0; i < DeltaLZ4DocValuesEncoder.BLOCK_SIZE; ++i) {
            if (i % 2 == 0) {
                arr[i] = Long.MIN_VALUE + random().nextInt(10000);
            } else {
                arr[i] = Long.MAX_VALUE - random().nextInt(10000);
            }
        }
        doTest(arr, -1);
    }

    public void testIncreasingValues() throws IOException {
        long[] arr = new long[DeltaLZ4DocValuesEncoder.BLOCK_SIZE];
        for (int i = 0; i < DeltaLZ4DocValuesEncoder.BLOCK_SIZE; ++i) {
            arr[i] = 30 * i - 1000;
        }
        long expectedNumBytes = 28;
        doTest(arr, expectedNumBytes);
    }

    public void testDecreasingValues() throws IOException {
        long[] arr = new long[DeltaLZ4DocValuesEncoder.BLOCK_SIZE];
        for (int i = 0; i < DeltaLZ4DocValuesEncoder.BLOCK_SIZE; ++i) {
            arr[i] = 1000 - 30 * i;
        }
        long expectedNumBytes = 28;
        doTest(arr, expectedNumBytes);
    }

    private void doTest(long[] arr, long expectedNumBytes) throws IOException {
        final long[] expected = arr.clone();
        DeltaLZ4DocValuesEncoder encoder = new DeltaLZ4DocValuesEncoder();
        for (int i = 0; i < expected.length; i++) {
            encoder.add(i, expected[i]);
        }
        try (Directory dir = newDirectory()) {
            try (IndexOutput out = dir.createOutput("tests.bin", IOContext.DEFAULT)) {
                encoder.encode(out);
                if (expectedNumBytes != -1) {
                    assertEquals(expectedNumBytes, out.getFilePointer());
                }
            }
            try (IndexInput in = dir.openInput("tests.bin", IOContext.DEFAULT)) {
                encoder.decode(in);
                assertEquals(in.length(), in.getFilePointer());

                long[] decoded = new long[expected.length];
                for (int i = 0; i < expected.length; i++) {
                    decoded[i] = encoder.get(i);
                }
                assertArrayEquals(expected, decoded);
            }
        }
    }
}
