// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License"),
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.ratelimiter;

enum RateLimitType {
  UPLOAD_PACK_PER_HOUR("uploadpackperhour"),
  UPLOAD_PACK_PER_HOUR_WARN("uploadpackperhourwarn");

  private final String type;

  private RateLimitType(String type) {
    this.type = type;
  }

  @Override
  public String toString() {
    return type;
  }

  static RateLimitType from(String value) {
    for (RateLimitType rateLimitType : RateLimitType.values()) {
      if (rateLimitType.toString().equalsIgnoreCase(value)) {
        return rateLimitType;
      }
    }
    return null;
  }
}
