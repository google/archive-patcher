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
 * Tests for a {@link MsDosDate} class.
 */
public class MsDosDateTest {
    private final static long FULL_TS_MILLIS = 1418418038000L; // 2014-12-12 at 21:00:38 UTC
    private final static long DATE_ONLY_TS_MILLIS = 1418342400000L; // 2014-12-12 at 00:00:00 UTC (hour/minute/second truncated)

    @Test
    @SuppressWarnings("javadoc")
    public void testFromMillisecondsSinceEpoch() {
        MsDosDate date = MsDosDate.fromMillisecondsSinceEpoch(FULL_TS_MILLIS);
        assertEquals(12, date.getDayOfMonth());
        assertEquals(12, date.getMonthOneBased());
        assertEquals(2014 - 1980, date.getYearOffsetFrom1980());
        assertEquals(DATE_ONLY_TS_MILLIS, date.asMillisecondsSinceEpoch());
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testConstructor() {
        MsDosDate date = new MsDosDate(12, 12, 2014-1980);
        assertEquals(12, date.getDayOfMonth());
        assertEquals(12, date.getMonthOneBased());
        assertEquals(2014 - 1980, date.getYearOffsetFrom1980());
        assertEquals(DATE_ONLY_TS_MILLIS, date.asMillisecondsSinceEpoch());
    }
}