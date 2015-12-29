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

package com.google.archivepatcher.reassembler;

/**
 * Different types of reassembly techniques.
 */
public enum ReassemblyTechnique {
    /**
     * Recompress a resource.
     */
    RECOMPRESS,

    /**
     * Copy a resource instead of recompressing, because it was not compressed
     * to begin with.
     */
    COPY_NO_COMPRESSION,

    /**
     * Copy a resource instead of recompressing, because the deflate parameters
     * needed to precisely replicate the output could not be determined. 
     */
    COPY_UNKNOWN_DEFLATE_PARAMETERS,

    /**
     * Copy a resource instead of recompressing, because the technology used to
     * perform the original compression could not be determined.
     */
    COPY_UNKNOWN_TECH;
}