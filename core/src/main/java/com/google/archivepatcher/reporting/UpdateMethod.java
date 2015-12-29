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

package com.google.archivepatcher.reporting;

/**
 * Enumeration of update methods used.
 */
public enum UpdateMethod {
    /**
     * Indicates that a resource update is performed by sending a full copy of
     * the resource data.
     */
    FULL_TRANSFER,

    /**
     * Indicates that a resource update is performed by sending a (possibly
     * compressed) delta that allows the new version of the resource to be
     * constructed using the old data and the delta.
     */
    DELTA_TRANSFER;
}
