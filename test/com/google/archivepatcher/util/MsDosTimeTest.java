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

package com.google.archivepatcher.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests for a {@link MsDosTime} class.
 */
public class MsDosTimeTest {
    private final static long FULL_TS_MILLIS = 1418418038000L; // 2014-12-12 at 21:00:38 UTC
    private final static long TIME_ONLY_EPOCH_TS_MILLIS = 75638000L; // 1970-01-01 at 21:00:38 UTC (no year, month, or day information stored)

    @Test
    @SuppressWarnings("javadoc")
    public void testFromMillisecondsSinceEpoch() {
        MsDosTime time = MsDosTime.fromMillisecondsSinceEpoch(FULL_TS_MILLIS);
        assertEquals(21, time.getHourOfDay());
        assertEquals(0, time.getMinuteOfHour());
        assertEquals(38 / 2, time.getSecondDividedBy2());
        assertEquals(TIME_ONLY_EPOCH_TS_MILLIS, time.asMillisecondsSinceMidnight());
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testConstructor() {
        MsDosTime time = new MsDosTime(38/2, 0, 21);
        assertEquals(21, time.getHourOfDay());
        assertEquals(0, time.getMinuteOfHour());
        assertEquals(38 / 2, time.getSecondDividedBy2());
        assertEquals(TIME_ONLY_EPOCH_TS_MILLIS, time.asMillisecondsSinceMidnight());
    }
}