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
package org.apache.lucene.document;

import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.PointValues;
import org.apache.lucene.search.IndexOrDocValuesQuery;
import org.apache.lucene.search.PointRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;

/**
 * Field that stores a per-document <code>double</code> value for scoring, sorting or value
 * retrieval and index the field for fast range filters. If you also need to store the value, you
 * should add a separate {@link StoredField} instance. If you need more fine-grained control you can
 * use {@link DoublePoint} and {@link DoubleDocValuesField}.
 *
 * <p>This field defines static factory methods for creating common queries:
 *
 * <ul>
 *   <li>{@link #newExactQuery(String, double)} for matching an exact 1D point.
 *   <li>{@link #newRangeQuery(String, double, double)} for matching a 1D range.
 * </ul>
 *
 * @see PointValues
 */
public final class DoubleField extends Field {
  /**
   * Creates a new DoubleField, indexing the provided point and storing it as a DocValue
   *
   * @param name field name
   * @param value the double value
   * @param sorted configure the field to support multiple DocValues
   * @throws IllegalArgumentException if the field name or value is null.
   */
  public DoubleField(String name, double value, boolean sorted) {
    this(name, Double.valueOf(value), sorted);
  }

  /**
   * Creates a new DoubleField, indexing the provided point and storing it as a DocValue
   *
   * @param name field name
   * @param value the double value
   * @param sorted configure the field to support multiple DocValues
   * @throws IllegalArgumentException if the field name or value is null.
   */
  public DoubleField(String name, Double value, boolean sorted) {
    super(name, getType(sorted));
    fieldsData = Double.doubleToRawLongBits(value);
  }

  @Override
  public BytesRef binaryValue() {
    byte[] encodedPoint = new byte[Double.BYTES];
    double value = getValueAsDouble();
    DoublePoint.encodeDimension(value, encodedPoint, 0);
    return new BytesRef(encodedPoint);
  }

  private double getValueAsDouble() {
    return Double.longBitsToDouble(numericValue().longValue());
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + " <" + name + ':' + getValueAsDouble() + '>';
  }

  private static FieldType getType(boolean sorted) {
    FieldType type = new FieldType();
    type.setDimensions(1, Double.BYTES);
    type.setDocValuesType(sorted ? DocValuesType.SORTED_NUMERIC : DocValuesType.NUMERIC);
    type.freeze();
    return type;
  }

  @Override
  public void setDoubleValue(double value) {
    super.setLongValue(Double.doubleToRawLongBits(value));
  }

  @Override
  public void setLongValue(long value) {
    throw new IllegalArgumentException("cannot change value type from Double to Long");
  }

  /**
   * Create a query for matching an exact double value.
   *
   * @param field field name. must not be {@code null}.
   * @param value exact value
   * @throws IllegalArgumentException if {@code field} is null.
   * @return a query matching documents with this exact value
   */
  public static Query newExactQuery(String field, double value) {
    return newRangeQuery(field, value, value);
  }

  /**
   * Create a range query for double values.
   *
   * <p>You can have half-open ranges (which are in fact &lt;/&le; or &gt;/&ge; queries) by setting
   * {@code lowerValue = Double.NEGATIVE_INFINITY} or {@code upperValue = Double.POSITIVE_INFINITY}.
   *
   * <p>Range comparisons are consistent with {@link Double#compareTo(Double)}.
   *
   * @param field field name. must not be {@code null}.
   * @param lowerValue lower portion of the range (inclusive).
   * @param upperValue upper portion of the range (inclusive).
   * @throws IllegalArgumentException if {@code field} is null.
   * @return a query matching documents within this range.
   */
  public static Query newRangeQuery(String field, double lowerValue, double upperValue) {
    PointRangeQuery.checkArgs(field, lowerValue, upperValue);
    return new IndexOrDocValuesQuery(
        DoublePoint.newRangeQuery(field, lowerValue, upperValue),
        NumericDocValuesField.newSlowRangeQuery(
            field,
            NumericUtils.doubleToSortableLong(lowerValue),
            NumericUtils.doubleToSortableLong(upperValue)));
  }
}