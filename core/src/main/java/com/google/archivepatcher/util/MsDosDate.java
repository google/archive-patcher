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

/**
 * A utility class for manipulating date data stored in MS-DOS format, based
 * upon a reading of the MS-DOS date format at
 * <a href='http://msdn.microsoft.com/en-us/library/windows/desktop/ms724247(v=vs.85).aspx'>
 * http://msdn.microsoft.com/en-us/library/windows/desktop/ms724247(v=vs.85).aspx</a>.
 * <p>
 * This is a <em>very</em> old format originating from a time when storage
 * requirements were very different. It packs into 16 bits a representation that
 * is valid from the first day of 1980 until the last day of 2107.
 * <p>
 * This class is threadsafe and immutable.
 * @see MsDosTime
 */
public class MsDosDate {
    private final int dayOfMonth;
    private final int monthOneBased;
    private final int yearOffsetFrom1980;

    /**
     * Constructs a new MS-DOS date using the specified values. Does not
     * ensure a valid date (it is possible to specify a date that does not
     * exist, such as February 30th of any year).
     * @param dayOfMonth the day of the month, in the range [1, 31].
     * @param monthOneBased the month of the year, in the range [1, 12].
     * @param yearOffsetFrom1980 the year as a positive offset from 1980, in
     * the range [0, 127].
     * @throws IllegalArgumentException if any of the specified values is out
     * of the allowed range
     */
    public MsDosDate(final int dayOfMonth, final int monthOneBased,
        final int yearOffsetFrom1980) {
        if (yearOffsetFrom1980 < 0 || yearOffsetFrom1980 > 127) {
            throw new IllegalArgumentException("yearOffsetFrom1980 must be " +
                "in the range [0, 127]: " + yearOffsetFrom1980);
        }
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            throw new IllegalArgumentException(
                "dayOfMonth must be in the range [1, 31]: " + dayOfMonth);
        }
        if (monthOneBased < 1 || monthOneBased > 12) {
            throw new IllegalArgumentException(
                "monthOneBased must be in the range [1, 12]: " + monthOneBased);
        }
        this.dayOfMonth = dayOfMonth;
        this.monthOneBased = monthOneBased;
        this.yearOffsetFrom1980 = yearOffsetFrom1980;
    }

    /**
     * Converts a raw 16-bit value from a ZIP archive into an MS-DOS date.
     * Note that just like the constructor, this method does not ensure that
     * the resulting object represents a valid date.
     * @param value_16bit the 16-bit "last modified date" from a ZIP file
     * @return the equivalent object
     */
    public static MsDosDate from16BitPackedValue(int value_16bit) {
        final int dayOfMonth = value_16bit & 31; // 5-bit value
        final int monthOneBased = (value_16bit >> 5) & 15; // 4-bit value
        final int yearOffsetFrom1980 = (value_16bit >> 9) & 127; // 7-bit value
        return new MsDosDate(dayOfMonth, monthOneBased, yearOffsetFrom1980);
    }

    /**
     * Converts this object to its raw 16-bit representation for use in a ZIP
     * archive's "last modified date" field.
     * @return such a representation
     */
    public int to16BitPackedValue() {
        int result = yearOffsetFrom1980;
        result <<= 4;
        result |= monthOneBased;
        result <<= 5;
        result |= dayOfMonth;
        return result;
    }

    /**
     * Returns an alternative representation of this object as milliseconds
     * since the epoch, UTC. The result is always at the start of the day
     * represented (zero seconds past midnight).
     * @return the date as milliseconds since the epoch, UTC.
     */
    public long asMillisecondsSinceEpoch() {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(0L);
        calendar.set(1980+yearOffsetFrom1980, monthOneBased-1, dayOfMonth);
        return calendar.getTimeInMillis();
    }

    /**
     * Inverts the logic of {@link #asMillisecondsSinceEpoch()} to convert a
     * timestamp expressed in milliseconds since the epoch, UTC, into an MS-DOS
     * date object. Note that the valid range for MS-DOS dates is from the
     * first date of 1980 till the last day of 2107; dates outside of this range
     * will result in an {@link IllegalArgumentException} being thrown.
     * @param millisUtc the timestamp to convert
     * @return the object as described
     * @throws IllegalArgumentException if the date cannot be represented in
     * this format
     */
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + dayOfMonth;
        result = prime * result + monthOneBased;
        result = prime * result + yearOffsetFrom1980;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        MsDosDate other = (MsDosDate) obj;
        if (dayOfMonth != other.dayOfMonth) return false;
        if (monthOneBased != other.monthOneBased) return false;
        if (yearOffsetFrom1980 != other.yearOffsetFrom1980) return false;
        return true;
    }

    /**
     * Returns the day of the month in the range [0, 31].
     * @return the value
     */
    public int getDayOfMonth() {
        return dayOfMonth;
    }

    /**
     * Returns the month of the year in the range [1, 12], where 1 is January
     * and 12 is December.
     * @return the value
     */
    public int getMonthOneBased() {
        return monthOneBased;
    }

    
    /**
     * Return the year as an offset from 1980.
     * @return the value
     */
    public int getYearOffsetFrom1980() {
        return yearOffsetFrom1980;
    }
}