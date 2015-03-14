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

/**
 * The default implementation of the {@link PatchPartResolver} interface, which
 * simply generates the built-in {@link PatchPart} objects for each
 * {@link PatchCommand} according to the following mapping:
 * <br>{@link PatchCommand#BEGIN} yields a {@link BeginMetadata} object
 * <br>{@link PatchCommand#COPY} yields an IllegalArgumentException
 * <br>{@link PatchCommand#NEW} yields a {@link NewMetadata} object
 * <br>{@link PatchCommand#PATCH} yields a {@link PatchMetadata} object
 * <br>{@link PatchCommand#REFRESH} yields a {@link RefreshMetadata} object
 */
public class DefaultPartResolver implements PatchPartResolver {

    @Override
    public PatchPart partFor(final PatchCommand command) {
        switch (command) {
            case BEGIN:     return new BeginMetadata();
            case NEW:       return new NewMetadata();
            case REFRESH:   return new RefreshMetadata();
            case PATCH:     return new PatchMetadata();
            case COPY:
                throw new IllegalArgumentException(
                    "COPY command takes no part!");
            default:
                throw new IllegalArgumentException(
                    "Unknown command: " + command);
        }
    }
}