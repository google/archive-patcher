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
import java.util.concurrent.TimeUnit;

// http://msdn.microsoft.com/en-us/library/windows/desktop/ms724247(v=vs.85).aspx
public class MsDosTime {
    private int secondDividedBy2;
    private int minuteOfHour;
    private int hourOfDay;

    public MsDosTime(int secondDividedBy2, int minuteOfHour, int hourOfDay) {
        this.secondDividedBy2 = secondDividedBy2;
        this.minuteOfHour = minuteOfHour;
        this.hourOfDay = hourOfDay;
    }

    public static MsDosTime from16BitPackedValue(int value_16bit) {
        int secondDividedBy2 = value_16bit & 31; // 5-bit value
        int minuteOfHour = (value_16bit >> 5) & 63; // 6-bit value
        int hourOfDay = (value_16bit >> 11) & 31; // 5-bit value
        return new MsDosTime(secondDividedBy2, minuteOfHour, hourOfDay);
    }

    public int to16BitPackedValue() {
        int result = hourOfDay;
        result <<= 6;
        result |= minuteOfHour;
        result <<= 5;
        result |= secondDividedBy2;
        return result;
    }

    public long asMillisecondsSinceMidnight() {
        long result = 0;
        result += TimeUnit.HOURS.toMillis(hourOfDay);
        result += TimeUnit.MINUTES.toMillis(minuteOfHour);
        result += TimeUnit.SECONDS.toMillis(secondDividedBy2 * 2);
        return result;
    }

    public static MsDosTime fromMillisecondsSinceEpoch(final long millisUtc) {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(millisUtc);
        final int secondDividedBy2 = calendar.get(Calendar.SECOND) / 2;
        final int minuteOfHour = calendar.get(Calendar.MINUTE);
        final int hourOfDay = calendar.get(Calendar.HOUR_OF_DAY);
        return new MsDosTime(secondDividedBy2, minuteOfHour, hourOfDay);
    }

    @Override
    public String toString() {
        DateFormat format = new SimpleDateFormat("HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(asMillisecondsSinceMidnight()));
    }
}