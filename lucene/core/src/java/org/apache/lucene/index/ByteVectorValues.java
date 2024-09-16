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
package org.apache.lucene.index;

import java.io.IOException;
import java.util.List;
import org.apache.lucene.document.KnnByteVectorField;
import org.apache.lucene.search.VectorScorer;

/**
 * This class provides access to per-document floating point vector values indexed as {@link
 * KnnByteVectorField}.
 *
 * @lucene.experimental
 */
public abstract class ByteVectorValues extends KnnVectorValues {

  /**
   * A dictionary of dense byte vectors.
   */
  public static abstract class Dictionary {

    /**
     * Return the vector value for the given vector ordinal which must be in [0, size() - 1],
     * otherwise IndexOutOfBoundsException is thrown. The returned array may be shared across calls.
     *
     * @return the vector value
     */
    public abstract byte[] vectorValue(int ord) throws IOException;
  }

  /** Sole constructor */
  protected ByteVectorValues() {}

  @Override
  public ByteVectorValues copy() throws IOException {
    return this;
  }

  /** Retrieve a {@link Dictionary} of vectors. */
  public Dictionary dictionary() throws IOException {
    ByteVectorValues copy = copy();
    return new Dictionary() {
      @Override
      public byte[] vectorValue(int ord) throws IOException {
        return copy.vectorValue(ord);
      }
    };
  }

  /**
   * Return the vector value for the given vector ordinal which must be in [0, size() - 1],
   * otherwise IndexOutOfBoundsException is thrown. The returned array may be shared across calls.
   *
   * @return the vector value
   */
  public byte[] vectorValue(int ord) throws IOException {
    return dictionary().vectorValue(ord);
  }

  /**
   * Checks the Vector Encoding of a field
   *
   * @throws IllegalStateException if {@code field} has vectors, but using a different encoding
   * @lucene.internal
   * @lucene.experimental
   */
  public static void checkField(LeafReader in, String field) {
    FieldInfo fi = in.getFieldInfos().fieldInfo(field);
    if (fi != null && fi.hasVectorValues() && fi.getVectorEncoding() != VectorEncoding.BYTE) {
      throw new IllegalStateException(
          "Unexpected vector encoding ("
              + fi.getVectorEncoding()
              + ") for field "
              + field
              + "(expected="
              + VectorEncoding.BYTE
              + ")");
    }
  }

  /**
   * Return a {@link VectorScorer} for the given query vector.
   *
   * @param query the query vector
   * @return a {@link VectorScorer} instance or null
   */
  public VectorScorer scorer(byte[] query) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public VectorEncoding getEncoding() {
    return VectorEncoding.BYTE;
  }

  /**
   * Creates a {@link ByteVectorValues} from a list of byte arrays.
   *
   * @param vectors the list of byte arrays
   * @param dim the dimension of the vectors
   * @return a {@link ByteVectorValues} instancec
   */
  public static ByteVectorValues fromBytes(List<byte[]> vectors, int dim) {
    return new ByteVectorValues() {
      @Override
      public int size() {
        return vectors.size();
      }

      @Override
      public int dimension() {
        return dim;
      }

      @Override
      public Dictionary dictionary() throws IOException {
        return new Dictionary() {
          @Override
          public byte[] vectorValue(int ord) throws IOException {
            return vectors.get(ord);
          }
        };
      }

      @Override
      public DocIndexIterator iterator() {
        return createDenseIterator();
      }
    };
  }
}
