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

public enum PatchCommand {
    NEW(1),
    COPY(2),
    REFRESH(3),
    BEGIN(4),
    PATCH(5);
    
    public final int binaryFormat;

    private PatchCommand(final int binaryFormat) {
        this.binaryFormat = binaryFormat;
    }

    public final static PatchCommand fromBinaryFormat(final int binaryFormat) {
        switch (binaryFormat) {
            case 1: return NEW;
            case 2: return COPY;
            case 3: return REFRESH;
            case 4: return BEGIN;
            case 5: return PATCH;
            default:
                throw new IllegalArgumentException("No such command: " + binaryFormat);
        }
    }
}