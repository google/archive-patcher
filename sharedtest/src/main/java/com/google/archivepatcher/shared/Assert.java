// Copyright 2016 Google Inc. All rights reserved.
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

package com.google.archivepatcher.shared;

/** This class provides helpers that we need for easier testing missing from JUnit 4. */
public class Assert {

  public interface CheckedSupplier<T> {
    T get() throws Exception;
  }

  public interface CheckedRunnable {
    void run() throws Exception;
  }

  // Below is a copy of the implementation of assertThrows and any helpers it need.

  /**
   * Asserts that {@code runnable} throws an exception of type {@code expectedThrowable} when
   * executed. If it does, the exception object is returned. If it does not throw an exception, an
   * {@link AssertionError} is thrown. If it throws the wrong type of exception, an {@code
   * AssertionError} is thrown describing the mismatch; the exception that was actually thrown can
   * be obtained by calling {@link AssertionError#getCause}.
   *
   * @param expectedThrowable the expected type of the exception
   * @param runnable a function that is expected to throw an exception when executed
   * @return the exception thrown by {@code runnable}
   * @since 4.13
   */
  public static <T extends Throwable> T assertThrows(
      Class<T> expectedThrowable, CheckedRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable actualThrown) {
      if (expectedThrowable.isInstance(actualThrown)) {
        @SuppressWarnings("unchecked")
        T retVal = (T) actualThrown;
        return retVal;
      } else {
        String expected = formatClass(expectedThrowable);
        Class<? extends Throwable> actualThrowable = actualThrown.getClass();
        String actual = formatClass(actualThrowable);
        if (expected.equals(actual)) {
          // There must be multiple class loaders. Add the identity hash code so the message
          // doesn't say "expected: java.lang.String<my.package.MyException> ..."
          expected += "@" + Integer.toHexString(System.identityHashCode(expectedThrowable));
          actual += "@" + Integer.toHexString(System.identityHashCode(actualThrowable));
        }
        String mismatchMessage = format("unexpected exception type thrown;", expected, actual);

        // The AssertionError(String, Throwable) ctor is only available on JDK7.
        AssertionError assertionError = new AssertionError(mismatchMessage);
        assertionError.initCause(actualThrown);
        throw assertionError;
      }
    }
    String notThrownMessage =
        String.format(
            "expected %s to be thrown, but nothing was thrown", formatClass(expectedThrowable));
    throw new AssertionError(notThrownMessage);
  }

  private static String formatClass(Class<?> value) {
    String className = value.getCanonicalName();
    return className == null ? value.getName() : className;
  }

  static String format(String message, Object expected, Object actual) {
    String formatted = "";
    if (message != null && !message.isEmpty()) {
      formatted = message + " ";
    }
    String expectedString = String.valueOf(expected);
    String actualString = String.valueOf(actual);
    if (expectedString.equals(actualString)) {
      return formatted
          + "expected: "
          + formatClassAndValue(expected, expectedString)
          + " but was: "
          + formatClassAndValue(actual, actualString);
    } else {
      return formatted + "expected:<" + expectedString + "> but was:<" + actualString + ">";
    }
  }

  private static String formatClassAndValue(Object value, String valueString) {
    String className = value == null ? "null" : value.getClass().getName();
    return className + "<" + valueString + ">";
  }
}
