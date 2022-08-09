// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
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

public enum RateLimitSeverity {
  SOFT_LIMIT("softlimit", "limit severity"),
  HARD_LIMIT("hardlimit", "limit severity");

  private final String severity;
  private final String limitSeverity;

  RateLimitSeverity(String severity, String limitSeverity) {
    this.severity = severity;
    this.limitSeverity = limitSeverity;
  }

  @Override
  public String toString() {
    return severity;
  }

  public String getLimitSeverity() {
    return limitSeverity;
  }

  static RateLimitSeverity from(String value) {
    for (RateLimitSeverity rateLimitSeverity : RateLimitSeverity.values()) {
      if (rateLimitSeverity.toString().equalsIgnoreCase(value)) {
        return rateLimitSeverity;
      }
    }
    return null;
  }
}
