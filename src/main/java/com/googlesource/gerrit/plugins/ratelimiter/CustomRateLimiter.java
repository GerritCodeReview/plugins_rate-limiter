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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class CustomRateLimiter implements RateLimiter {
  private final Semaphore semaphore;
  private final int maxPermits;
  private final AtomicInteger usedPermits;
  private final ScheduledFuture<?> replenishTask;
  public static final long DEFAULT_HOUR = 60L;

  interface Factory {
    CustomRateLimiter create(int permits, long timeLapse);
  }

  @Inject
  CustomRateLimiter(
      @RateLimitExecutor ScheduledExecutorService executor,
      @Assisted int permits,
      @Assisted long timeLapse) {
    this.semaphore = new Semaphore(permits);
    this.maxPermits = permits;
    this.usedPermits = new AtomicInteger();
    this.replenishTask =
        executor.scheduleAtFixedRate(this::replenishPermits, 1, timeLapse, TimeUnit.MINUTES);
  }

  @Override
  public int permitsPerHour() {
    return maxPermits;
  }

  @Override
  public synchronized boolean acquirePermit() {
    boolean permit = semaphore.tryAcquire();
    if (permit) {
      usedPermits.getAndIncrement();
    }
    return permit;
  }

  @Override
  public int availablePermits() {
    return semaphore.availablePermits();
  }

  @Override
  public int usedPermits() {
    return usedPermits.get();
  }

  @Override
  public long remainingTime(TimeUnit timeUnit) {
    return replenishTask.getDelay(timeUnit);
  }

  @Override
  public synchronized void replenishPermits() {
    semaphore.release(usedPermits());
    usedPermits.set(0);
  }

  @Override
  public void close() {
    replenishTask.cancel(true);
  }
}
