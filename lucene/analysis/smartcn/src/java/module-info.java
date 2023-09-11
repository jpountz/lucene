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

/** Analyzer for indexing Chinese */
module org.apache.lucene.analysis.smartcn {
  requires org.apache.lucene.core;
  requires org.apache.lucene.analysis.common;

  exports org.apache.lucene.analysis.cn.smart;
  exports org.apache.lucene.analysis.cn.smart.hhmm;

  opens org.apache.lucene.analysis.cn.smart to
      org.apache.lucene.core;

  provides org.apache.lucene.analysis.TokenizerFactory with
      org.apache.lucene.analysis.cn.smart.HMMChineseTokenizerFactory;
}
