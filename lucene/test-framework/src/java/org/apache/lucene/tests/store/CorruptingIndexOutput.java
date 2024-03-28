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

package org.apache.lucene.tests.store;

import java.io.IOException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterIndexOutput;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

/** Corrupts on bit of a file after close */
public class CorruptingIndexOutput extends FilterIndexOutput {
  final Directory dir;
  final long byteToCorrupt;
  private boolean closed;

  public CorruptingIndexOutput(Directory dir, long byteToCorrupt, IndexOutput out) {
    super("CorruptingIndexOutput(" + out + ")", out.getName(), out);
    this.dir = dir;
    this.byteToCorrupt = byteToCorrupt;
  }

  @Override
  public String getName() {
    return out.getName();
  }

  @Override
  public void close() throws IOException {
    if (closed == false) {
      out.close();
      // NOTE: must corrupt after file is closed, because if we corrupt "inlined" (as bytes are
      // being written) the checksum sees the wrong
      // bytes and is "correct"!!
      corruptFile();
      closed = true;
    }
  }

  protected void corruptFile() throws IOException {
    // Now corrupt the specfied byte:
    String newTempName;
    try (IndexOutput tmpOut = dir.createTempOutput("tmp", "tmp", IOContext.WRITE);
        IndexInput in = dir.openInput(out.getName(), IOContext.READ)) {
      newTempName = tmpOut.getName();

      if (byteToCorrupt >= in.length()) {
        throw new IllegalArgumentException(
            "byteToCorrupt="
                + byteToCorrupt
                + " but file \""
                + out.getName()
                + "\" is only length="
                + in.length());
      }

      tmpOut.copyBytes(in, byteToCorrupt);
      // Flip the 0th bit:
      tmpOut.writeByte((byte) (in.readByte() ^ 1));
      tmpOut.copyBytes(in, in.length() - byteToCorrupt - 1);
    }

    // Delete original and copy corrupt version back:
    dir.deleteFile(out.getName());
    dir.copyFrom(dir, newTempName, out.getName());
    dir.deleteFile(newTempName);
  }

  @Override
  public long getChecksum() throws IOException {
    return out.getChecksum() ^ 1;
  }

  @Override
  public void writeBytes(byte[] b, int offset, int length) throws IOException {
    for (int i = 0; i < length; i++) {
      writeByte(b[offset + i]);
    }
  }
}
