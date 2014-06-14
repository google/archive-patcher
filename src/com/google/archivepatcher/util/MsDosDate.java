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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

// http://msdn.microsoft.com/en-us/library/windows/desktop/ms724247(v=vs.85).aspx
public class MsDosDate {
    private int dayOfMonth;
    private int monthOneBased;
    private int yearOffsetFrom1980;

    public MsDosDate(int dayOfMonth, int monthOneBased, int yearOffsetFrom1980) {
        this.dayOfMonth = dayOfMonth;
        this.monthOneBased = monthOneBased;
        this.yearOffsetFrom1980 = yearOffsetFrom1980;
    }

    public static MsDosDate from16BitPackedValue(int value_16bit) {
        int dayOfMonth = value_16bit & 31; // 5-bit value
        int monthOneBased = (value_16bit >> 5) & 15; // 4-bit value
        int yearOffsetFrom1980 = (value_16bit >> 9) & 127; // 7-bit value
        return new MsDosDate(dayOfMonth, monthOneBased, yearOffsetFrom1980);
    }

    public int to16BitPackedValue() {
        int result = yearOffsetFrom1980;
        result <<= 4;
        result |= monthOneBased;
        result <<= 5;
        result |= dayOfMonth;
        return result;
    }

    public long asMillisecondsSinceEpoch() {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.set(1980+yearOffsetFrom1980, monthOneBased-1, dayOfMonth);
        return calendar.getTimeInMillis();
    }

    public static MsDosDate fromMillisecondsSinceEpoch(final long millisUtc) {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(millisUtc);
        final int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
        final int monthOneBased = calendar.get(Calendar.MONTH) + 1;
        final int yearOffsetFrom1980 = calendar.get(Calendar.YEAR) - 1980;
        return new MsDosDate(dayOfMonth, monthOneBased, yearOffsetFrom1980);
    }

    @Override
    public String toString() {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(asMillisecondsSinceEpoch()));
    }
}
