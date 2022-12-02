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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.function.BiFunction;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.DoubleField;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.FloatField;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/*
 * Very simple tests of sorting.
 *
 * THE RULES:
 * 1. keywords like 'abstract' and 'static' should not appear in this file.
 * 2. each test method should be self-contained and understandable.
 * 3. no test methods should share code with other test methods.
 * 4. no testing of things unrelated to sorting.
 * 5. no tracers.
 * 6. keyword 'class' should appear only once in this file, here ----
 *                                                                  |
 *        -----------------------------------------------------------
 *        |
 *       \./
 */
public class TestSort extends LuceneTestCase {

  private void assertEquals(Sort a, Sort b) {
    LuceneTestCase.assertEquals(a, b);
    LuceneTestCase.assertEquals(b, a);
    LuceneTestCase.assertEquals(a.hashCode(), b.hashCode());
  }

  private void assertDifferent(Sort a, Sort b) {
    assertNotEquals(a, b);
    assertNotEquals(b, a);
    assertNotEquals(a.hashCode(), b.hashCode());
  }

  public void testEquals() {
    SortField sortField1 = new SortField("foo", SortField.Type.STRING);
    SortField sortField2 = new SortField("foo", SortField.Type.STRING);
    assertEquals(new Sort(sortField1), new Sort(sortField2));

    sortField2 = new SortField("bar", SortField.Type.STRING);
    assertDifferent(new Sort(sortField1), new Sort(sortField2));

    sortField2 = new SortField("foo", SortField.Type.LONG);
    assertDifferent(new Sort(sortField1), new Sort(sortField2));

    sortField2 = new SortField("foo", SortField.Type.STRING);
    sortField2.setMissingValue(SortField.STRING_FIRST);
    assertDifferent(new Sort(sortField1), new Sort(sortField2));

    sortField2 = new SortField("foo", SortField.Type.STRING, false);
    assertEquals(new Sort(sortField1), new Sort(sortField2));

    sortField2 = new SortField("foo", SortField.Type.STRING, true);
    assertDifferent(new Sort(sortField1), new Sort(sortField2));
  }

  /** Tests sorting on type string */
  public void testString() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(new SortedDocValuesField("value", newBytesRef("foo")));
    doc.add(newStringField("value", "foo", Field.Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(new SortedDocValuesField("value", newBytesRef("bar")));
    doc.add(newStringField("value", "bar", Field.Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.STRING));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(2, td.totalHits.value);
    // 'bar' comes before 'foo'
    assertEquals("bar", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertEquals("foo", searcher.doc(td.scoreDocs[1].doc).get("value"));

    ir.close();
    dir.close();
  }

  /** Tests reverse sorting on type string */
  public void testStringReverse() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(new SortedDocValuesField("value", newBytesRef("bar")));
    doc.add(newStringField("value", "bar", Field.Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(new SortedDocValuesField("value", newBytesRef("foo")));
    doc.add(newStringField("value", "foo", Field.Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.STRING, true));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(2, td.totalHits.value);
    // 'foo' comes after 'bar' in reverse order
    assertEquals("foo", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertEquals("bar", searcher.doc(td.scoreDocs[1].doc).get("value"));

    ir.close();
    dir.close();
  }

  /** Tests sorting on type string_val */
  public void testStringVal() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(new BinaryDocValuesField("value", newBytesRef("foo")));
    doc.add(newStringField("value", "foo", Field.Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(new BinaryDocValuesField("value", newBytesRef("bar")));
    doc.add(newStringField("value", "bar", Field.Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.STRING_VAL));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(2, td.totalHits.value);
    // 'bar' comes before 'foo'
    assertEquals("bar", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertEquals("foo", searcher.doc(td.scoreDocs[1].doc).get("value"));

    ir.close();
    dir.close();
  }

  /** Tests reverse sorting on type string_val */
  public void testStringValReverse() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(new BinaryDocValuesField("value", newBytesRef("bar")));
    doc.add(newStringField("value", "bar", Field.Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(new BinaryDocValuesField("value", newBytesRef("foo")));
    doc.add(newStringField("value", "foo", Field.Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.STRING_VAL, true));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(2, td.totalHits.value);
    // 'foo' comes after 'bar' in reverse order
    assertEquals("foo", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertEquals("bar", searcher.doc(td.scoreDocs[1].doc).get("value"));

    ir.close();
    dir.close();
  }

  /** Tests sorting on type int */
  public void testInt() throws IOException {
    doTestIntSorting(NumericDocValuesField::new);
  }

  public void testIntField() throws IOException {
    doTestIntSorting((name, value) -> new IntField(name, value, false));
  }

  private void doTestIntSorting(BiFunction<String, Integer, IndexableField> intFieldSupplier)
      throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(intFieldSupplier.apply("value", 300000));
    doc.add(newStringField("value", "300000", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(intFieldSupplier.apply("value", -1));
    doc.add(newStringField("value", "-1", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(intFieldSupplier.apply("value", 4));
    doc.add(newStringField("value", "4", Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.INT));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(3, td.totalHits.value);
    // numeric order
    assertEquals("-1", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertEquals("4", searcher.doc(td.scoreDocs[1].doc).get("value"));
    assertEquals("300000", searcher.doc(td.scoreDocs[2].doc).get("value"));

    ir.close();
    dir.close();
  }

  /** Tests sorting on type int in reverse */
  public void testIntReverse() throws IOException {
    doTestIntReverse(NumericDocValuesField::new);
  }

  public void testIntFieldReverse() throws IOException {
    doTestIntReverse((name, value) -> new IntField(name, value, false));
  }

  private void doTestIntReverse(BiFunction<String, Integer, IndexableField> intFieldSupplier)
      throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(intFieldSupplier.apply("value", 300000));
    doc.add(newStringField("value", "300000", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(intFieldSupplier.apply("value", -1));
    doc.add(newStringField("value", "-1", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(intFieldSupplier.apply("value", 4));
    doc.add(newStringField("value", "4", Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.INT, true));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(3, td.totalHits.value);
    // reverse numeric order
    assertEquals("300000", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertEquals("4", searcher.doc(td.scoreDocs[1].doc).get("value"));
    assertEquals("-1", searcher.doc(td.scoreDocs[2].doc).get("value"));

    ir.close();
    dir.close();
  }

  /** Tests sorting on type int with a missing value */
  public void testIntMissing() throws IOException {
    doTestIntMissing(NumericDocValuesField::new);
  }

  public void testIntFieldMissing() throws IOException {
    doTestIntMissing((name, value) -> new IntField(name, value, false));
  }

  private void doTestIntMissing(BiFunction<String, Integer, IndexableField> intFieldSupplier)
      throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    writer.addDocument(doc);
    doc = new Document();
    doc.add(intFieldSupplier.apply("value", -1));
    doc.add(newStringField("value", "-1", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(intFieldSupplier.apply("value", 4));
    doc.add(newStringField("value", "4", Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.INT));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(3, td.totalHits.value);
    // null is treated as a 0
    assertEquals("-1", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertNull(searcher.doc(td.scoreDocs[1].doc).get("value"));
    assertEquals("4", searcher.doc(td.scoreDocs[2].doc).get("value"));

    ir.close();
    dir.close();
  }

  /**
   * Tests sorting on type int, specifying the missing value should be treated as Integer.MAX_VALUE
   */
  public void testIntMissingLast() throws IOException {
    doTestIntMissingLast(NumericDocValuesField::new);
  }

  public void testIntFieldMissingLast() throws IOException {
    doTestIntMissingLast((name, value) -> new IntField(name, value, false));
  }

  private void doTestIntMissingLast(BiFunction<String, Integer, IndexableField> intFieldSupplier)
      throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    writer.addDocument(doc);
    doc = new Document();
    doc.add(intFieldSupplier.apply("value", -1));
    doc.add(newStringField("value", "-1", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(intFieldSupplier.apply("value", 4));
    doc.add(newStringField("value", "4", Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    SortField sortField = new SortField("value", SortField.Type.INT);
    sortField.setMissingValue(Integer.MAX_VALUE);
    Sort sort = new Sort(sortField);

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(3, td.totalHits.value);
    // null is treated as a Integer.MAX_VALUE
    assertEquals("-1", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertEquals("4", searcher.doc(td.scoreDocs[1].doc).get("value"));
    assertNull(searcher.doc(td.scoreDocs[2].doc).get("value"));

    ir.close();
    dir.close();
  }

  /** Tests sorting on type long */
  public void testLong() throws IOException {
    doTestLongSorting(NumericDocValuesField::new);
  }

  public void testLongField() throws IOException {
    doTestLongSorting((name, value) -> new LongField(name, value, false));
  }

  private void doTestLongSorting(BiFunction<String, Long, IndexableField> longFieldSupplier)
      throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(longFieldSupplier.apply("value", 3000000000L));
    doc.add(newStringField("value", "3000000000", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(longFieldSupplier.apply("value", -1L));
    doc.add(newStringField("value", "-1", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(longFieldSupplier.apply("value", 4L));
    doc.add(newStringField("value", "4", Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.LONG));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(3, td.totalHits.value);
    // numeric order
    assertEquals("-1", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertEquals("4", searcher.doc(td.scoreDocs[1].doc).get("value"));
    assertEquals("3000000000", searcher.doc(td.scoreDocs[2].doc).get("value"));

    ir.close();
    dir.close();
  }

  /** Tests sorting on type long in reverse */
  public void testLongReverse() throws IOException {
    doTestLongReverseSorting(NumericDocValuesField::new);
  }

  public void testLongFieldReverse() throws IOException {
    doTestLongReverseSorting((name, value) -> new LongField(name, value, false));
  }

  private void doTestLongReverseSorting(BiFunction<String, Long, IndexableField> longFieldSupplier)
      throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(longFieldSupplier.apply("value", 3000000000L));
    doc.add(newStringField("value", "3000000000", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(longFieldSupplier.apply("value", -1L));
    doc.add(newStringField("value", "-1", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(longFieldSupplier.apply("value", 4L));
    doc.add(newStringField("value", "4", Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.LONG, true));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(3, td.totalHits.value);
    // reverse numeric order
    assertEquals("3000000000", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertEquals("4", searcher.doc(td.scoreDocs[1].doc).get("value"));
    assertEquals("-1", searcher.doc(td.scoreDocs[2].doc).get("value"));

    ir.close();
    dir.close();
  }

  /** Tests sorting on type long with a missing value */
  public void testLongMissing() throws IOException {
    doTestLongSortWithMissingFields(NumericDocValuesField::new);
  }

  public void testLongFieldMissing() throws IOException {
    doTestLongSortWithMissingFields((name, value) -> new LongField(name, value, false));
  }

  private void doTestLongSortWithMissingFields(
      BiFunction<String, Long, IndexableField> longFieldSupplier) throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    writer.addDocument(doc);
    doc = new Document();
    doc.add(longFieldSupplier.apply("value", -1L));
    doc.add(newStringField("value", "-1", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(longFieldSupplier.apply("value", 4L));
    doc.add(newStringField("value", "4", Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.LONG));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(3, td.totalHits.value);
    // null is treated as 0
    assertEquals("-1", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertNull(searcher.doc(td.scoreDocs[1].doc).get("value"));
    assertEquals("4", searcher.doc(td.scoreDocs[2].doc).get("value"));

    ir.close();
    dir.close();
  }

  /**
   * Tests sorting on type long, specifying the missing value should be treated as Long.MAX_VALUE
   */
  public void testLongMissingLast() throws IOException {
    doTestLongMissingLast(NumericDocValuesField::new);
  }

  public void testLongFieldMissingLast() throws IOException {
    doTestLongMissingLast((name, value) -> new LongField(name, value, false));
  }

  private void doTestLongMissingLast(BiFunction<String, Long, IndexableField> longFieldSupplier)
      throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    writer.addDocument(doc);
    doc = new Document();
    doc.add(longFieldSupplier.apply("value", -1L));
    doc.add(newStringField("value", "-1", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(longFieldSupplier.apply("value", 4L));
    doc.add(newStringField("value", "4", Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    SortField sortField = new SortField("value", SortField.Type.LONG);
    sortField.setMissingValue(Long.MAX_VALUE);
    Sort sort = new Sort(sortField);

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(3, td.totalHits.value);
    // null is treated as Long.MAX_VALUE
    assertEquals("-1", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertEquals("4", searcher.doc(td.scoreDocs[1].doc).get("value"));
    assertNull(searcher.doc(td.scoreDocs[2].doc).get("value"));

    ir.close();
    dir.close();
  }

  /** Tests sorting on type float */
  public void testFloat() throws IOException {
    doTestFloatSorting(FloatDocValuesField::new);
  }

  public void testFloatFields() throws IOException {
    doTestFloatSorting((name, value) -> new FloatField(name, value, false));
  }

  private void doTestFloatSorting(BiFunction<String, Float, IndexableField> floatFieldSupplier)
      throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(floatFieldSupplier.apply("value", 30.1F));
    doc.add(newStringField("value", "30.1", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(floatFieldSupplier.apply("value", -1.3F));
    doc.add(newStringField("value", "-1.3", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(floatFieldSupplier.apply("value", 4.2F));
    doc.add(newStringField("value", "4.2", Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.FLOAT));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(3, td.totalHits.value);
    // numeric order
    assertEquals("-1.3", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertEquals("4.2", searcher.doc(td.scoreDocs[1].doc).get("value"));
    assertEquals("30.1", searcher.doc(td.scoreDocs[2].doc).get("value"));

    ir.close();
    dir.close();
  }

  /** Tests sorting on type float in reverse */
  public void testFloatReverse() throws IOException {
    doTestFloatReverse(FloatDocValuesField::new);
  }

  public void testFloatFieldReverse() throws IOException {
    doTestFloatReverse((name, value) -> new FloatField(name, value, false));
  }

  private void doTestFloatReverse(BiFunction<String, Float, IndexableField> floatFieldSupplier)
      throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(floatFieldSupplier.apply("value", 30.1F));
    doc.add(newStringField("value", "30.1", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(floatFieldSupplier.apply("value", -1.3F));
    doc.add(newStringField("value", "-1.3", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(floatFieldSupplier.apply("value", 4.2F));
    doc.add(newStringField("value", "4.2", Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.FLOAT, true));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(3, td.totalHits.value);
    // reverse numeric order
    assertEquals("30.1", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertEquals("4.2", searcher.doc(td.scoreDocs[1].doc).get("value"));
    assertEquals("-1.3", searcher.doc(td.scoreDocs[2].doc).get("value"));

    ir.close();
    dir.close();
  }

  /** Tests sorting on type float with a missing value */
  public void testFloatMissing() throws IOException {
    doTestFloatMissing(FloatDocValuesField::new);
  }

  public void testFloatFieldMissing() throws IOException {
    doTestFloatMissing((name, value) -> new FloatField(name, value, false));
  }

  private void doTestFloatMissing(BiFunction<String, Float, IndexableField> floatFieldSupplier)
      throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    writer.addDocument(doc);
    doc = new Document();
    doc.add(floatFieldSupplier.apply("value", -1.3F));
    doc.add(newStringField("value", "-1.3", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(floatFieldSupplier.apply("value", 4.2F));
    doc.add(newStringField("value", "4.2", Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.FLOAT));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(3, td.totalHits.value);
    // null is treated as 0
    assertEquals("-1.3", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertNull(searcher.doc(td.scoreDocs[1].doc).get("value"));
    assertEquals("4.2", searcher.doc(td.scoreDocs[2].doc).get("value"));

    ir.close();
    dir.close();
  }

  /**
   * Tests sorting on type float, specifying the missing value should be treated as Float.MAX_VALUE
   */
  public void testFloatMissingLast() throws IOException {
    doTestFloatMissingLast(FloatDocValuesField::new);
  }

  public void testFloatFieldMissingLast() throws IOException {
    doTestFloatMissingLast((name, value) -> new FloatField(name, value, false));
  }

  private void doTestFloatMissingLast(BiFunction<String, Float, IndexableField> floatFieldSupplier)
      throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    writer.addDocument(doc);
    doc = new Document();
    doc.add(floatFieldSupplier.apply("value", -1.3F));
    doc.add(newStringField("value", "-1.3", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(floatFieldSupplier.apply("value", 4.2F));
    doc.add(newStringField("value", "4.2", Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    SortField sortField = new SortField("value", SortField.Type.FLOAT);
    sortField.setMissingValue(Float.MAX_VALUE);
    Sort sort = new Sort(sortField);

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(3, td.totalHits.value);
    // null is treated as Float.MAX_VALUE
    assertEquals("-1.3", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertEquals("4.2", searcher.doc(td.scoreDocs[1].doc).get("value"));
    assertNull(searcher.doc(td.scoreDocs[2].doc).get("value"));

    ir.close();
    dir.close();
  }

  /** Tests sorting on type double */
  public void testDouble() throws IOException {
    doTestDouble(DoubleDocValuesField::new);
  }

  public void testDoubleField() throws IOException {
    doTestDouble((name, value) -> new DoubleField(name, value, false));
  }

  private void doTestDouble(BiFunction<String, Double, IndexableField> doubleFieldSupplier)
      throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(doubleFieldSupplier.apply("value", 30.1));
    doc.add(newStringField("value", "30.1", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(doubleFieldSupplier.apply("value", -1.3));
    doc.add(newStringField("value", "-1.3", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(doubleFieldSupplier.apply("value", 4.2333333333333));
    doc.add(newStringField("value", "4.2333333333333", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(doubleFieldSupplier.apply("value", 4.2333333333332));
    doc.add(newStringField("value", "4.2333333333332", Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.DOUBLE));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(4, td.totalHits.value);
    // numeric order
    assertEquals("-1.3", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertEquals("4.2333333333332", searcher.doc(td.scoreDocs[1].doc).get("value"));
    assertEquals("4.2333333333333", searcher.doc(td.scoreDocs[2].doc).get("value"));
    assertEquals("30.1", searcher.doc(td.scoreDocs[3].doc).get("value"));

    ir.close();
    dir.close();
  }

  /** Tests sorting on type double with +/- zero */
  public void testDoubleSignedZero() throws IOException {
    doTestDoubleSignedZero(DoubleDocValuesField::new);
  }

  public void testDoubleFieldSignedZero() throws IOException {
    doTestDoubleSignedZero((name, value) -> new DoubleField(name, value, false));
  }

  private void doTestDoubleSignedZero(
      BiFunction<String, Double, IndexableField> doubleFieldSupplier) throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(doubleFieldSupplier.apply("value", +0D));
    doc.add(newStringField("value", "+0", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(doubleFieldSupplier.apply("value", -0D));
    doc.add(newStringField("value", "-0", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.DOUBLE));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(2, td.totalHits.value);
    // numeric order
    assertEquals("-0", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertEquals("+0", searcher.doc(td.scoreDocs[1].doc).get("value"));

    ir.close();
    dir.close();
  }

  /** Tests sorting on type double in reverse */
  public void testDoubleReverse() throws IOException {
    doTestDoubleReverse(DoubleDocValuesField::new);
  }

  public void testDoubleFieldReverse() throws IOException {
    doTestDoubleReverse((name, value) -> new DoubleField(name, value, false));
  }

  private void doTestDoubleReverse(BiFunction<String, Double, IndexableField> doubleFieldSupplier)
      throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(doubleFieldSupplier.apply("value", 30.1));
    doc.add(newStringField("value", "30.1", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(doubleFieldSupplier.apply("value", -1.3));
    doc.add(newStringField("value", "-1.3", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(doubleFieldSupplier.apply("value", 4.2333333333333));
    doc.add(newStringField("value", "4.2333333333333", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(doubleFieldSupplier.apply("value", 4.2333333333332));
    doc.add(newStringField("value", "4.2333333333332", Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.DOUBLE, true));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(4, td.totalHits.value);
    // numeric order
    assertEquals("30.1", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertEquals("4.2333333333333", searcher.doc(td.scoreDocs[1].doc).get("value"));
    assertEquals("4.2333333333332", searcher.doc(td.scoreDocs[2].doc).get("value"));
    assertEquals("-1.3", searcher.doc(td.scoreDocs[3].doc).get("value"));

    ir.close();
    dir.close();
  }

  /** Tests sorting on type double with a missing value */
  public void testDoubleMissing() throws IOException {
    doTestDoubleMissing(DoubleDocValuesField::new);
  }

  public void testDoubleFieldMissing() throws IOException {
    doTestDoubleMissing((name, value) -> new DoubleField(name, value, false));
  }

  private void doTestDoubleMissing(BiFunction<String, Double, IndexableField> doubleFieldSupplier)
      throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    writer.addDocument(doc);
    doc = new Document();
    doc.add(doubleFieldSupplier.apply("value", -1.3));
    doc.add(newStringField("value", "-1.3", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(doubleFieldSupplier.apply("value", 4.2333333333333));
    doc.add(newStringField("value", "4.2333333333333", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(doubleFieldSupplier.apply("value", 4.2333333333332));
    doc.add(newStringField("value", "4.2333333333332", Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.DOUBLE));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(4, td.totalHits.value);
    // null treated as a 0
    assertEquals("-1.3", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertNull(searcher.doc(td.scoreDocs[1].doc).get("value"));
    assertEquals("4.2333333333332", searcher.doc(td.scoreDocs[2].doc).get("value"));
    assertEquals("4.2333333333333", searcher.doc(td.scoreDocs[3].doc).get("value"));

    ir.close();
    dir.close();
  }

  /**
   * Tests sorting on type double, specifying the missing value should be treated as
   * Double.MAX_VALUE
   */
  public void testDoubleMissingLast() throws IOException {
    doTestDoubleMissingLast(DoubleDocValuesField::new);
  }

  public void testDoubleFieldMissingLast() throws IOException {
    doTestDoubleMissingLast((name, value) -> new DoubleField(name, value, false));
  }

  private void doTestDoubleMissingLast(
      BiFunction<String, Double, IndexableField> doubleFieldSupplier) throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    writer.addDocument(doc);
    doc = new Document();
    doc.add(doubleFieldSupplier.apply("value", -1.3));
    doc.add(newStringField("value", "-1.3", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(doubleFieldSupplier.apply("value", 4.2333333333333));
    doc.add(newStringField("value", "4.2333333333333", Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(doubleFieldSupplier.apply("value", 4.2333333333332));
    doc.add(newStringField("value", "4.2333333333332", Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    SortField sortField = new SortField("value", SortField.Type.DOUBLE);
    sortField.setMissingValue(Double.MAX_VALUE);
    Sort sort = new Sort(sortField);

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(4, td.totalHits.value);
    // null treated as Double.MAX_VALUE
    assertEquals("-1.3", searcher.doc(td.scoreDocs[0].doc).get("value"));
    assertEquals("4.2333333333332", searcher.doc(td.scoreDocs[1].doc).get("value"));
    assertEquals("4.2333333333333", searcher.doc(td.scoreDocs[2].doc).get("value"));
    assertNull(searcher.doc(td.scoreDocs[3].doc).get("value"));

    ir.close();
    dir.close();
  }

  /** Tests sorting on multiple sort fields */
  public void testMultiSort() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(new SortedDocValuesField("value1", newBytesRef("foo")));
    doc.add(new NumericDocValuesField("value2", 0));
    doc.add(newStringField("value1", "foo", Field.Store.YES));
    doc.add(newStringField("value2", "0", Field.Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(new SortedDocValuesField("value1", newBytesRef("bar")));
    doc.add(new NumericDocValuesField("value2", 1));
    doc.add(newStringField("value1", "bar", Field.Store.YES));
    doc.add(newStringField("value2", "1", Field.Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(new SortedDocValuesField("value1", newBytesRef("bar")));
    doc.add(new NumericDocValuesField("value2", 0));
    doc.add(newStringField("value1", "bar", Field.Store.YES));
    doc.add(newStringField("value2", "0", Field.Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(new SortedDocValuesField("value1", newBytesRef("foo")));
    doc.add(new NumericDocValuesField("value2", 1));
    doc.add(newStringField("value1", "foo", Field.Store.YES));
    doc.add(newStringField("value2", "1", Field.Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort =
        new Sort(
            new SortField("value1", SortField.Type.STRING),
            new SortField("value2", SortField.Type.LONG));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(4, td.totalHits.value);
    // 'bar' comes before 'foo'
    assertEquals("bar", searcher.doc(td.scoreDocs[0].doc).get("value1"));
    assertEquals("bar", searcher.doc(td.scoreDocs[1].doc).get("value1"));
    assertEquals("foo", searcher.doc(td.scoreDocs[2].doc).get("value1"));
    assertEquals("foo", searcher.doc(td.scoreDocs[3].doc).get("value1"));
    // 0 comes before 1
    assertEquals("0", searcher.doc(td.scoreDocs[0].doc).get("value2"));
    assertEquals("1", searcher.doc(td.scoreDocs[1].doc).get("value2"));
    assertEquals("0", searcher.doc(td.scoreDocs[2].doc).get("value2"));
    assertEquals("1", searcher.doc(td.scoreDocs[3].doc).get("value2"));

    // Now with overflow
    td = searcher.search(new MatchAllDocsQuery(), 1, sort);
    assertEquals(4, td.totalHits.value);
    assertEquals("bar", searcher.doc(td.scoreDocs[0].doc).get("value1"));
    assertEquals("0", searcher.doc(td.scoreDocs[0].doc).get("value2"));

    ir.close();
    dir.close();
  }

  public void testRewrite() throws IOException {
    try (Directory dir = newDirectory()) {
      RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
      IndexSearcher searcher = newSearcher(writer.getReader());
      writer.close();

      LongValuesSource longSource = LongValuesSource.constant(1L);
      Sort sort = new Sort(longSource.getSortField(false));

      assertSame(sort, sort.rewrite(searcher));

      DoubleValuesSource doubleSource = DoubleValuesSource.constant(1.0);
      sort = new Sort(doubleSource.getSortField(false));

      assertSame(sort, sort.rewrite(searcher));
    }
  }

  // Ghost tests make sure that sorting can cope with segments that are missing values while their
  // FieldInfo reports that the field exists.

  public void testStringGhost() throws IOException {
    doTestStringGhost(true);
    doTestStringGhost(false);
  }

  private void doTestStringGhost(boolean indexed) throws IOException {
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig());
    Document doc = new Document();
    doc.add(new SortedDocValuesField("value", newBytesRef("foo")));
    if (indexed) {
      doc.add(newStringField("value", "foo", Field.Store.YES));
    }
    doc.add(new StringField("id", "0", Store.NO));
    writer.addDocument(doc);
    writer.addDocument(new Document());
    writer.flush();
    writer.addDocument(new Document());
    writer.flush();
    writer.deleteDocuments(new Term("id", "0"));
    writer.forceMerge(1);
    IndexReader ir = DirectoryReader.open(writer);
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.STRING));

    TopFieldDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(2, td.totalHits.value);
    assertNull(((FieldDoc) td.scoreDocs[0]).fields[0]);
    assertNull(((FieldDoc) td.scoreDocs[1]).fields[0]);

    ir.close();
    dir.close();
  }

  public void testIntGhost() throws IOException {
    doTestIntGhost(true);
    doTestIntGhost(false);
  }

  private void doTestIntGhost(boolean indexed) throws IOException {
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig());
    Document doc = new Document();
    doc.add(new NumericDocValuesField("value", 3));
    if (indexed) {
      doc.add(new IntPoint("value", 3));
    }
    doc.add(new StringField("id", "0", Store.NO));
    writer.addDocument(doc);
    writer.addDocument(new Document());
    writer.flush();
    writer.addDocument(new Document());
    writer.flush();
    writer.deleteDocuments(new Term("id", "0"));
    writer.forceMerge(1);
    IndexReader ir = DirectoryReader.open(writer);
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.INT));

    TopFieldDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(2, td.totalHits.value);
    assertEquals(0, ((FieldDoc) td.scoreDocs[0]).fields[0]);
    assertEquals(0, ((FieldDoc) td.scoreDocs[1]).fields[0]);

    ir.close();
    dir.close();
  }

  public void testLongGhost() throws IOException {
    doTestLongGhost(true);
    doTestLongGhost(false);
  }

  private void doTestLongGhost(boolean indexed) throws IOException {
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig());
    Document doc = new Document();
    doc.add(new NumericDocValuesField("value", 3L));
    if (indexed) {
      doc.add(new LongPoint("value", 3L));
    }
    doc.add(new StringField("id", "0", Store.NO));
    writer.addDocument(doc);
    writer.addDocument(new Document());
    writer.flush();
    writer.addDocument(new Document());
    writer.flush();
    writer.deleteDocuments(new Term("id", "0"));
    writer.forceMerge(1);
    IndexReader ir = DirectoryReader.open(writer);
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.LONG));

    TopFieldDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(2, td.totalHits.value);
    assertEquals(0L, ((FieldDoc) td.scoreDocs[0]).fields[0]);
    assertEquals(0L, ((FieldDoc) td.scoreDocs[1]).fields[0]);

    ir.close();
    dir.close();
  }

  public void testDoubleGhost() throws IOException {
    doTestDoubleGhost(true);
    doTestDoubleGhost(false);
  }

  private void doTestDoubleGhost(boolean indexed) throws IOException {
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig());
    Document doc = new Document();
    doc.add(new DoubleDocValuesField("value", 1.25));
    if (indexed) {
      doc.add(new DoublePoint("value", 1.25));
    }
    doc.add(new StringField("id", "0", Store.NO));
    writer.addDocument(doc);
    writer.addDocument(new Document());
    writer.flush();
    writer.addDocument(new Document());
    writer.flush();
    writer.deleteDocuments(new Term("id", "0"));
    writer.forceMerge(1);
    IndexReader ir = DirectoryReader.open(writer);
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.DOUBLE));

    TopFieldDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(2, td.totalHits.value);
    assertEquals(0.0, ((FieldDoc) td.scoreDocs[0]).fields[0]);
    assertEquals(0.0, ((FieldDoc) td.scoreDocs[1]).fields[0]);

    ir.close();
    dir.close();
  }

  public void testFloatGhost() throws IOException {
    doTestFloatGhost(true);
    doTestFloatGhost(false);
  }

  private void doTestFloatGhost(boolean indexed) throws IOException {
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig());
    Document doc = new Document();
    doc.add(new FloatDocValuesField("value", 1.25f));
    if (indexed) {
      doc.add(new FloatPoint("value", 1.25f));
    }
    doc.add(new StringField("id", "0", Store.NO));
    writer.addDocument(doc);
    writer.addDocument(new Document());
    writer.flush();
    writer.addDocument(new Document());
    writer.flush();
    writer.deleteDocuments(new Term("id", "0"));
    writer.forceMerge(1);
    IndexReader ir = DirectoryReader.open(writer);
    writer.close();

    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortField("value", SortField.Type.FLOAT));

    TopFieldDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(2, td.totalHits.value);
    assertEquals(0.0f, ((FieldDoc) td.scoreDocs[0]).fields[0]);
    assertEquals(0.0f, ((FieldDoc) td.scoreDocs[1]).fields[0]);

    ir.close();
    dir.close();
  }
}
