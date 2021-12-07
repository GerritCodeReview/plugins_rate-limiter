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

import java.util.Comparator;
import java.util.concurrent.TimeUnit;

interface RateLimiter extends Comparable<RateLimiter> {

  @Override
  public default int compareTo(RateLimiter other) {
    return Comparator.comparing(RateLimiter::availablePermits).reversed().compare(this, other);
  }

  /** Returns number of permits allowed per hour. */
  int permitsPerHour();

  /**
   * Acquire an available permit if any left.
   *
   * @return true if permit was acquired, otherwise false.
   */
  boolean acquirePermit();

  /** Returns the number of available permits left. */
  int availablePermits();

  /** Returns the number of permits used in the hour. */
  int usedPermits();

  /** Returns remaining time before available permits are replenished, in the given time unit. */
  long remainingTime(TimeUnit timeUnit);

  /** Replenish available permits to the number allowed per hour. */
  void replenishPermits();

  /** Return type of rate limiter * */
  String getType();

  /** Closes this RateLimiter, relinquishing any underlying resources. */
  void close();
}
