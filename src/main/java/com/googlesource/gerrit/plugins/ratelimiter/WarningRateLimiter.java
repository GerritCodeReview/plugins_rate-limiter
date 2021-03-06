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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

class WarningRateLimiter implements RateLimiter {
  @FunctionalInterface
  interface Factory {
    WarningRateLimiter create(RateLimiter delegate, String key, int warnLimit, long timeLapse);
  }

  private static final Logger rateLimitLog = RateLimiterStatsLog.getLogger();
  private static final DateTimeFormatter format = DateTimeFormatter.ofPattern("mm 'min' ss 'sec'");

  private final UserResolver userResolver;
  private final RateLimiter delegate;
  private final int warnLimit;
  private final String key;
  private final long timeLapse;

  private volatile boolean wasLogged;
  private volatile boolean warningWasLogged = false;

  @Inject
  WarningRateLimiter(
      UserResolver userResolver,
      @Assisted RateLimiter delegate,
      @Assisted String key,
      @Assisted int warnLimit,
      @Assisted long timeLapse) {
    this.userResolver = userResolver;
    this.delegate = delegate;
    this.warnLimit = warnLimit;
    this.key = key;
    this.timeLapse = timeLapse;
  }

  @Override
  public int permitsPerHour() {
    return delegate.permitsPerHour();
  }

  @Override
  public synchronized boolean acquirePermit() {
    boolean acquirePermit = delegate.acquirePermit();
    if (usedPermits() == warnLimit) {
      rateLimitLog.info(
          "{} reached the warning limit of {} uploadpacks per {} minutes.",
          userResolver.getUserName(key).orElse(key),
          warnLimit,
          timeLapse);
      warningWasLogged = true;
    }

    if (!acquirePermit && !wasLogged) {
      rateLimitLog.info(
          "{} was blocked due to exceeding the limit of {} uploadpacks per {} minutes."
              + " {} remaining to permits replenishing.",
          userResolver.getUserName(key).orElse(key),
          permitsPerHour(),
          timeLapse,
          secondsToMsSs(remainingTime(TimeUnit.SECONDS)));
      wasLogged = true;
    }
    return acquirePermit;
  }

  @Override
  public int availablePermits() {
    return delegate.availablePermits();
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
  public void close() {
    delegate.close();
  }

  @Override
  public int usedPermits() {
    return delegate.usedPermits();
  }

  private String secondsToMsSs(long seconds) {
    return LocalTime.MIN.plusSeconds(seconds).format(format);
  }

  @VisibleForTesting
  public boolean getWarningFlagState() {
    return warningWasLogged;
  }
}
