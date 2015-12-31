// Copyright 2015 Google Inc. All rights reserved.
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

package com.google.archivepatcher.util;

import java.lang.reflect.Method;

/**
 * A flexible timing class that runs on both vanilla JREs and Android JREs,
 * allowing thread CPU times to be measured with millisecond precision.
 */
public final class ThreadTiming {
    /**
     * The method to invoke in order to retrieve the current thread's CPU time.
     * For the vanilla JRE, this is
     * java.lang.management.ThreadMXBean.getCurrentThreadCpuTime().
     * For Android JREs, this is
     * android.os.SystemClock.currentThreadTimeMillis().
     */
    private final Method timingMethod;

    /**
     * The object upon which to invoke {@link #timingMethod}, if the method is
     * non-static. For the vanilla JRE, this is a ThreadMXBean. For Android
     * JREs, this is null (a static method is used).
     */
    private final Object timingObject;

    /**
     * A divisor to apply to the value returned by {@link #timingMethod}.
     * For the vanilla JRE, this is 1,000,000 (to convert nanos to millis).
     * For Android JREs, this is 1 (value already in millis).
     */
    private final long divisor;

    /**
     * Create a new timing object for the current thread.
     */
    public ThreadTiming() {
        Object tempObject = null;
        Method tempMethod = null;
        long tempDivisor = -1;
        boolean ok = false;
        try {
            // Attempt 
            Class<?> androidClass = Class.forName("android.os.SystemClock");
            tempMethod = androidClass.getMethod(
                "currentThreadTimeMillis", (Class<?>[]) null);
            // Already uses milliseconds
            tempDivisor = 1L;
            ok = true;
        } catch (Exception e) {
            tempObject = null;
            tempMethod = null;

            // Try the MxBean method instead
            try {
                Class<?> managementFactoryClass =
                    Class.forName("java.lang.management.ManagementFactory");
                Method mxBeanAccessorMethod =
                    managementFactoryClass.getMethod(
                        "getThreadMXBean", (Class<?>[]) null);
                Class<?> mxBeanClass = Class.forName(
                    "java.lang.management.ThreadMXBean");
                Object beanObject =
                    mxBeanAccessorMethod.invoke(null, (Object[]) null);
                Method supportCheckMethod = mxBeanClass.getMethod(
                    "isCurrentThreadCpuTimeSupported", (Class<?>[]) null);
                Boolean isSupported = (Boolean)
                    supportCheckMethod.invoke(beanObject, (Object[]) null);
                if (isSupported) {
                    tempObject = beanObject;
                    tempMethod = mxBeanClass.getMethod(
                        "getCurrentThreadCpuTime", (Class<?>[]) null);
                    tempDivisor = 1000000L; // Uses nanos instead of millis
                    ok = true;
                };
            } catch (Exception e2) {
                // No workaround. Give up.
            }
        }
        if (ok) {
            timingObject = tempObject;
            timingMethod = tempMethod;
            divisor = tempDivisor;
        } else {
            timingObject = null;
            timingMethod = null;
            divisor = -1L;
        }
    }

    /**
     * Checks if timing is properly supported on this thread.
     * @return true if so
     */
    public static boolean isTimingSupported() {
        return new ThreadTiming().timingMethod != null;
    }

    /**
     * Returns a current indication of thread execution time, in millis.
     * @return as described
     */
    public long getThreadCpuTimeMillis() {
        if (timingMethod == null) {
            return 0;
        }
        try {
            Long rawValue = (Long)
                timingMethod.invoke(timingObject, (Object[]) null);
            return rawValue / divisor;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}