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

package com.google.archivepatcher;

/**
 * Encapsulates JRE-compatible deflate parameters.
 */
public final class JreDeflateParameters {

    /**
     * The level of the deflate compressor.
     */
    public final int level;

    /**
     * The strategy used by the deflate compressor.
     */
    public final int strategy;

    /**
     * Whether or not nowrap is enabled for the deflate compressor.
     */
    public final boolean nowrap;

    /**
     * Creates a new parameters object having the specified configuration.
     * @param level the level for the deflate compressor
     * @param strategy the strategy for the deflate compressor
     * @param nowrap whether or not nowrap is enabled for the deflate compressor
     */
    public JreDeflateParameters(int level, int strategy, boolean nowrap) {
        super();
        this.level = level;
        this.strategy = strategy;
        this.nowrap = nowrap;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + level;
        result = prime * result + (nowrap ? 1231 : 1237);
        result = prime * result + strategy;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        JreDeflateParameters other = (JreDeflateParameters) obj;
        if (level != other.level) return false;
        if (nowrap != other.nowrap) return false;
        if (strategy != other.strategy) return false;
        return true;
    }

    @Override
    public String toString() {
        return "level=" + level + ",strategy="
            + strategy + ",nowrap=" + nowrap;
    }

    /**
     * Given an input string formatted like the output of {@link #toString()},
     * parse the string into an instance of this class.
     * @param input the input string to parse
     * @return an equivalent object of this class
     */
    public static JreDeflateParameters parseJreDeflateParameters(String input) {
        String[] parts = input.split(",");
        return new JreDeflateParameters(
            Integer.parseInt(parts[0].split("=")[1]),
            Integer.parseInt(parts[1].split("=")[1]),
            Boolean.parseBoolean(parts[2].split("=")[1]));
    }
}