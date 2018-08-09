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

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class WarningHourlyRateLimiter implements RateLimiter {
  @FunctionalInterface
  interface Factory {
    WarningHourlyRateLimiter create(RateLimiter delegate, String key, int warnLimit);
  }

  public static final Logger rateLimitLog = LoggerFactory.getLogger(RateLimiterStatsLog.LOG_NAME);
  public static final DateTimeFormatter format = DateTimeFormatter.ofPattern("mm 'min' ss 'sec'");

  private final UserResolver userResolver;
  private final RateLimiter delegate;
  private final int warnLimit;
  private final String key;

  private volatile boolean wasLogged;

  @Inject
  WarningHourlyRateLimiter(
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
    return delegate.permitsPerHour();
  }

  @Override
  public boolean acquirePermit() {
    if (permitsPerHour() - availablePermits() == warnLimit) {
      rateLimitLog.info(
          "{} exceeded the warning limit of {} uploadpacks per hour.",
          userResolver.getUserName(key).orElse(key),
          warnLimit);
    }
    boolean acquirePermit = delegate.acquirePermit();
    if (!acquirePermit && !wasLogged) {
      rateLimitLog.info(
          "{} was blocked due to exceeding the limit of {} uploadpacks per hour."
              + " {} remaining to permits replenishing.",
          userResolver.getUserName(key).orElse(key),
          permitsPerHour(),
          secondsToMsSs(remainingTime(TimeUnit.SECONDS)));
      wasLogged = true;
    }
    return acquirePermit;
  }

  private String secondsToMsSs(long seconds) {
    return LocalTime.MIN.plusSeconds(seconds).format(format);
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
    delegate.replenishPermits();
  }

  @Override
  public void close() {
    delegate.close();
  }
}
