// Copyright 2016 Google Inc. All rights reserved.
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

package com.google.archivepatcher.generator;

/** Reasons for a corresponding {@link DeltaFormat} */
public enum DeltaFormatExplanation {
  /** The delta format is chosen by default since no other rules apply. */
  DEFAULT,

  /** The delta format is chosen because it is more efficient for this file type. */
  FILE_TYPE,

  /**
   * This explanation is given in situations where more efficient delta formats exist but the
   * current sub-optimal delta format chosen due to resource constraints (e.g., total recompression
   * size limit).
   */
  RESOURCE_CONSTRAINED
}
