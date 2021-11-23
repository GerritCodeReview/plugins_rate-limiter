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

import java.util.concurrent.TimeUnit;

class UnlimitedRateLimiter implements RateLimiter {

  static final UnlimitedRateLimiter INSTANCE = new UnlimitedRateLimiter();

  private String rateLimitType = "";

  private UnlimitedRateLimiter() {}

  @Override
  public int maxPermits() {
    return Integer.MAX_VALUE;
  }

  @Override
  public boolean acquirePermit() {
    return true;
  }

  @Override
  public int availablePermits() {
    return Integer.MAX_VALUE;
  }

  @Override
  public long remainingTime(TimeUnit timeUnit) {
    return 0;
  }

  @Override
  public void replenishPermits() {
    // do nothing
  }
  
  public void setType(String rateLimitType) {
    this.rateLimitType = rateLimitType;
  }

  @Override
  public String getType() {
    return rateLimitType;
  }

  @Override
  public int usedPermits() {
    return 0;
  }

  @Override
  public void close() {
    // do nothing
  }
}
