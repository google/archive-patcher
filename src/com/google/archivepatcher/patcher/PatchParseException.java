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

package com.google.archivepatcher.patcher;

import java.io.IOException;

/**
 * Thrown when a patch can't be parsed due to corruption.
 */
public class PatchParseException extends IOException {

    private static final long serialVersionUID = -3919288740522087848L;

    /**
     * Constructs a new exception having the specified message and cause.
     * @param message the message
     * @param cause the cause
     */
    public PatchParseException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new exception having the specified message and no cause.
     * @param message the message
     */
    public PatchParseException(String message) {
        this(message, null);
    }
}
