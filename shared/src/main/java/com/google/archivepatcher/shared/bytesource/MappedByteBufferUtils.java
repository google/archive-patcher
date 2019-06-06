// Copyright 2017 Google LLC. All rights reserved.
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

package com.google.archivepatcher.shared.bytesource;

import java.lang.reflect.Method;
import java.nio.MappedByteBuffer;
import javax.annotation.Nullable;

/** Utilities to manipulate {@link java.nio.MappedByteBuffer} */
public class MappedByteBufferUtils {

  private static final String TAG = "MappedByteBufferUtils";

  @Nullable private static final Method directBufferFree;
  @Nullable private static final Method directBufferCleaner;
  @Nullable private static final Method cleanerClean;

  private MappedByteBufferUtils() {}

  /** Return true if we know how to unmap {@link MappedByteBuffer} instances on current platform. */
  public static boolean canFreeMappedBuffers() {
    return cleanerClean != null || directBufferFree != null;
  }

  /** Unmap specified {@link MappedByteBuffer} from memory. */
  public static void freeBuffer(MappedByteBuffer mappedByteBuffer)
      throws ReflectiveOperationException {
    if (cleanerClean != null) { // OpenJDK
      directBufferCleaner.setAccessible(true);
      Object cleaner = directBufferCleaner.invoke(mappedByteBuffer);
      cleanerClean.setAccessible(true);
      cleanerClean.invoke(cleaner);
    }

    if (directBufferFree != null) { // pre-OpenJDK
      directBufferFree.setAccessible(true);
      directBufferFree.invoke(mappedByteBuffer);
    }
  }

  static {
    Class<?> directBufferClass;

    try {
      directBufferClass = Class.forName("java.nio.DirectByteBuffer");
    } catch (Exception e) {
      directBufferClass = null;
    }

    if (directBufferClass != null) {
      directBufferFree = getMethodOrNull(directBufferClass, "free");
      directBufferCleaner = getMethodOrNull(directBufferClass, "cleaner");
      cleanerClean =
          directBufferCleaner != null
              ? getMethodOrNull(directBufferCleaner.getReturnType(), "clean")
              : null;
    } else {
      directBufferFree = null;
      directBufferCleaner = null;
      cleanerClean = null;
    }
  }

  private static Method getMethodOrNull(Class<?> cls, String name) {
    try {
      return cls.getMethod(name);
    } catch (Exception e) {
      return null;
    }
  }
}
