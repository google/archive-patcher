// Copyright 2014 Google Inc. All rights reserved.
// 
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.archivepatcher.patcher;

import com.google.archivepatcher.parts.Part;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

public class BinaryPartForTest implements Part {
    int expectedLength = -1;
    byte[] content = null;

    public BinaryPartForTest(byte[] content) {
        this.content = content;
    }

    public BinaryPartForTest(String content) {
        this.content = content.getBytes(Charset.forName("UTF8"));
    }

    public BinaryPartForTest(int expectedLength) {
        this.expectedLength = expectedLength;
    }

    @Override
    public void read(DataInput input) throws IOException {
        content = new byte[expectedLength];
        input.readFully(content);
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.write(content);
    }

    @Override
    public int getStructureLength() {
        return expectedLength >= 0 ? expectedLength : content.length;
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(content);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BinaryPartForTest other = (BinaryPartForTest) obj;
        if (!Arrays.equals(content, other.content))
            return false;
        return true;
    }

}