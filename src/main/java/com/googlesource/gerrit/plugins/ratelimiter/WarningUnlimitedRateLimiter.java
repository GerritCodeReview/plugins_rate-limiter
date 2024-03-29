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

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

class WarningUnlimitedRateLimiter implements RateLimiter {
  @FunctionalInterface
  interface Factory {
    WarningUnlimitedRateLimiter create(RateLimiter delegate, String key, int warnLimit);
  }

  private static final Logger rateLimitLog = RateLimiterStatsLog.getLogger();

  private final UserResolver userResolver;
  private final RateLimiter delegate;
  private final int warnLimit;
  private final String key;
  private volatile boolean warningWasLogged = false;

  @Inject
  WarningUnlimitedRateLimiter(
      UserResolver userResolver,
      @Assisted RateLimiter delegate,
      @Assisted String key,
      @Assisted int warnLimit) {
    this.userResolver = userResolver;
    this.delegate = delegate;
    this.warnLimit = warnLimit;
    this.key = key;
  }

  @Override
  public int permitsPerHour() {
    return Integer.MAX_VALUE;
  }

  @Override
  public boolean acquirePermit() {
    boolean acquirePermit = delegate.acquirePermit();

    if (acquirePermit && (usedPermits() == warnLimit)) {
      rateLimitLog.info(
          "{} reached the warning limit of {} {} per {} minutes.",
          userResolver.getUserName(key).orElse(key),
          warnLimit,
          delegate.getType(),
          delegate.getTimeLapse());
      warningWasLogged = true;
    }
    return acquirePermit;
  }

  @Override
  public int availablePermits() {
    return Integer.MAX_VALUE;
  }

  @Override
  public long remainingTime(TimeUnit timeUnit) {
    return delegate.remainingTime(timeUnit);
  }

  @Override
  public void replenishPermits() {
    warningWasLogged = false;
    delegate.replenishPermits();
  }

  @Override
  public String getType() {
    return delegate.getType();
  }

  @Override
  public Optional<Integer> getTimeLapse() {
    return delegate.getTimeLapse();
  }

  @Override
  public int usedPermits() {
    return delegate.usedPermits();
  }

  @Override
  public void close() {
    delegate.close();
  }

  @Override
  public Optional<Integer> getWarnLimit() {
    return Optional.of(warnLimit);
  }

  @VisibleForTesting
  public boolean getWarningFlagState() {
    return warningWasLogged;
  }
}
