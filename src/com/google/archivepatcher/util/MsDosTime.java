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

/**
 * A utility class for manipulating time data stored in MS-DOS format, based
 * upon a reading of the MS-DOS time format at
 * <a href='http://msdn.microsoft.com/en-us/library/windows/desktop/ms724247(v=vs.85).aspx'>
 * http://msdn.microsoft.com/en-us/library/windows/desktop/ms724247(v=vs.85).aspx</a>.
 * <p>
 * This is a <em>very</em> old format originating from a time when storage
 * requirements were very different. It packs into 16 bits a representation that
 * has a maximum resolution of 2 seconds, meaning that it is not possible to
 * represent a time whose seconds value is odd.
 * <p>
 * This class is threadsafe and immutable.
 * @see MsDosDate
 */
public class MsDosTime {
    private final int secondDividedBy2;
    private final int minuteOfHour;
    private final int hourOfDay;

    /**
     * Constructs a new MS-DOS time using the specified values, and ensures
     * that it is sane.
     * @param secondDividedBy2 the second within the hour, after being divided
     * by two; must be within the range [0,29].
     * @param minuteOfHour the minute within the hour, in the range [0,59]
     * @param hourOfDay the hour of the day, within the range [0, 23]
     * @throws IllegalArgumentException if any of the specified values is out
     * of the allowed range
     */
    public MsDosTime(final int secondDividedBy2, final int minuteOfHour,
        final int hourOfDay) {
        if (secondDividedBy2 < 0 || secondDividedBy2 > 29) {
            throw new IllegalArgumentException("secondDividedBy2 must be in " +
                "the range [0, 29]: " + secondDividedBy2);
        }
        if (minuteOfHour < 0 || minuteOfHour > 59) {
            throw new IllegalArgumentException("minuteOfHour must be in the " +
                "range [0, 59]: " + minuteOfHour);
        }
        if (hourOfDay < 0 || hourOfDay > 23) {
            throw new IllegalArgumentException("hourOfDay must be in the " +
                "range [0, 23]: " + hourOfDay);
        }
        this.secondDividedBy2 = secondDividedBy2;
        this.minuteOfHour = minuteOfHour;
        this.hourOfDay = hourOfDay;
    }

    /**
     * Converts a raw 16-bit value from a ZIP archive into an MS-DOS time.
     * @param value_16bit the 16-bit "last modified time" from a ZIP file
     * @return the equivalent object
     */
    public static MsDosTime from16BitPackedValue(int value_16bit) {
        int secondDividedBy2 = value_16bit & 31; // 5-bit value
        int minuteOfHour = (value_16bit >> 5) & 63; // 6-bit value
        int hourOfDay = (value_16bit >> 11) & 31; // 5-bit value
        return new MsDosTime(secondDividedBy2, minuteOfHour, hourOfDay);
    }

    /**
     * Converts this object to its raw 16-bit representation for use in a ZIP
     * archive's "last modified time" field.
     * @return such a representation
     */
    public int to16BitPackedValue() {
        int result = hourOfDay;
        result <<= 6;
        result |= minuteOfHour;
        result <<= 5;
        result |= secondDividedBy2;
        return result;
    }

    /**
     * Returns an alternative representation of this object as milliseconds
     * since midnight. The result is always in the range [0, 1439999] and is
     * always evenly divisible by 2000 (due to the MS-DOS limitation that the
     * number of seconds must always be even, and the coarse granularity of
     * the format).
     * @return the time as milliseconds since midnight
     */
    public long asMillisecondsSinceMidnight() {
        return TimeUnit.HOURS.toMillis(hourOfDay) +
            TimeUnit.MINUTES.toMillis(minuteOfHour) +
            TimeUnit.SECONDS.toMillis(secondDividedBy2 * 2);
    }

    /**
     * Inverts the logic of {@link #asMillisecondsSinceMidnight()} to convert a
     * timestamp expressed in milliseconds since the epoch, UTC, into an MS-DOS
     * time object. All date information is discarded; only the time of day is
     * preserved. Note that the finest resolution of MS-DOS timestamps is two
     * seconds, so the value will be truncated to the nearest second that is
     * evenly divisible by two.
     * @param millisUtc the timestamp to convert
     * @return the object as described
     * @throws IllegalArgumentException if the time cannot be represented in
     * this format
     */
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + hourOfDay;
        result = prime * result + minuteOfHour;
        result = prime * result + secondDividedBy2;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        MsDosTime other = (MsDosTime) obj;
        if (hourOfDay != other.hourOfDay) return false;
        if (minuteOfHour != other.minuteOfHour) return false;
        if (secondDividedBy2 != other.secondDividedBy2) return false;
        return true;
    }

    /**
     * Returns the seconds in the minute divided by two, in the range [0, 30].
     * @return the value
     */
    public int getSecondDividedBy2() {
        return secondDividedBy2;
    }

    
    /**
     * Returns the minute in the hour, in the range [0, 59].
     * @return the value
     */
    public int getMinuteOfHour() {
        return minuteOfHour;
    }

    /**
     * Returns the hour of the day, in the range [0, 23]
     * @return the value
     */
    public int getHourOfDay() {
        return hourOfDay;
    }
}