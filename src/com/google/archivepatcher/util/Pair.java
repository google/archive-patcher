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

/**
 * Simple helper class containing a pair of items.
 * @param <T>
 */
public final class Pair<T> {
    /**
     * The first value in the pair.
     */
    public final T value1;

    /**
     * The second value in the pair.
     */
    public final T value2;

    /**
     * Constructs a new pair.
     * @param value1 the first value
     * @param value2 the second value
     */
    public Pair(T value1, T value2) {
        this.value1 = value1;
        this.value2 = value2;
    }

    @Override
    public String toString() {
        return "Pair [value1=" + value1 + ", value2=" + value2 + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((value1 == null) ? 0 : value1.hashCode());
        result = prime * result + ((value2 == null) ? 0 : value2.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        @SuppressWarnings("rawtypes")
        Pair other = (Pair) obj;
        if (value1 == null) {
            if (other.value1 != null) return false;
        } else if (!value1.equals(other.value1)) return false;
        if (value2 == null) {
            if (other.value2 != null) return false;
        } else if (!value2.equals(other.value2)) return false;
        return true;
    }
}