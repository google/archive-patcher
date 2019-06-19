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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import javax.annotation.Nullable;

/** Utilities to manipulate {@link java.nio.MappedByteBuffer} */
public class MappedByteBufferUtils {

  private static final String TAG = "MappedByteBufferUtils";

  @Nullable private static Method oldCleaner;
  @Nullable private static Method oldClean;
  @Nullable private static Method clean;
  @Nullable private static Object theUnsafe;

  private MappedByteBufferUtils() {}

  /** Return true if we know how to unmap {@link MappedByteBuffer} instances on current platform. */
  public static boolean canFreeMappedBuffers() {
    return (oldClean != null && oldCleaner != null) || (clean != null && theUnsafe != null);
  }

  /** Unmap specified {@link MappedByteBuffer} from memory. */
  public static void freeBuffer(MappedByteBuffer mappedByteBuffer)
      throws ReflectiveOperationException {
    if (oldCleaner != null && oldClean != null) {
      oldCleaner.setAccessible(true);
      oldClean.setAccessible(true);
      oldClean.invoke(oldCleaner.invoke(mappedByteBuffer));
      return;
    }

    if (clean != null && theUnsafe != null) {
      clean.setAccessible(true);
      clean.invoke(theUnsafe, mappedByteBuffer);
    }
  }

  static {
    boolean isOldJDK = System.getProperty("java.specification.version", "99").startsWith("1.");
    try {
      if (isOldJDK) {
        oldCleaner = ByteBuffer.class.getMethod("cleaner");
        oldClean = Class.forName("sun.misc.Cleaner").getMethod("clean");
      } else {
        Class<?> unsafeClass;
        try {
          unsafeClass = Class.forName("sun.misc.Unsafe");
        } catch (Exception ex) {
          // jdk.internal.misc.Unsafe doesn't yet have an invokeCleaner() method,
          // but that method should be added if sun.misc.Unsafe is removed.
          unsafeClass = Class.forName("jdk.internal.misc.Unsafe");
        }
        clean = unsafeClass.getMethod("invokeCleaner", ByteBuffer.class);
        Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafeField.setAccessible(true);
        theUnsafe = theUnsafeField.get(null);
      }
    } catch (Exception ex) {
    }
  }
}
