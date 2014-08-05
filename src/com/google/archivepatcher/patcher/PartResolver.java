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

import com.google.archivepatcher.parts.Part;

/**
 * Interface for a {@link Part} factory that creates {@link Part} objects
 * for {@link PatchCommand}s. Test classes and other behavior-modifiers
 * can use this interface to generate custom implementations of the various
 * {@link Part}s for {@link PatchDirective}.
 */
public interface PartResolver {
    /**
     * Generates a new {@link Part} object appropriate for the specified
     * {@link PatchCommand}. For example, given a
     * {@link PatchCommand#REFRESH} the implementation should return an
     * object of type {@link RefreshMetadata} or a subclass thereof. 
     * @param command the command to generate a part for
     * @return the part
     */
    public Part partFor(PatchCommand command);
}