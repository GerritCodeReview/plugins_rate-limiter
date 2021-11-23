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

import static com.google.common.truth.Truth.assertThat;
import static com.googlesource.gerrit.plugins.ratelimiter.PeriodicRateLimiter.DEFAULT_TIME_LAPSE_IN_MINUTES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class PeriodicRateLimiterTest {

  private static final int RATE = 1000;

  private PeriodicRateLimiter limiter;
  private ScheduledExecutorService scheduledExecutorMock;

  @Before
  public void setUp() {
    scheduledExecutorMock = mock(ScheduledExecutorService.class);
    limiter = new PeriodicRateLimiter(scheduledExecutorMock, RATE, DEFAULT_TIME_LAPSE_IN_MINUTES, "Any Type");
  }

  @Test
  public void testGetRatePerHour() {
    assertThat(limiter.maxPermits()).isEqualTo(RATE);
  }

  @Test
  public void testAcquire() {
    assertThat(limiter.availablePermits()).isEqualTo(RATE);

    for (int i = 1; i <= 1000; i++) {
      assertThat(limiter.acquirePermit()).isTrue();
      assertThat(limiter.availablePermits()).isEqualTo(RATE - i);
    }
    assertThat(limiter.acquirePermit()).isFalse();
    assertThat(limiter.availablePermits()).isEqualTo(0);
  }

  @Test
  public void testReplenishPermits() {
    testAcquire();
    limiter.replenishPermits();
    testAcquire();
  }

  @Test
  public void testReplenishPermitsIsScheduled() {
    verify(scheduledExecutorMock)
        .scheduleAtFixedRate(
            any(),
            eq(DEFAULT_TIME_LAPSE_IN_MINUTES),
            eq(DEFAULT_TIME_LAPSE_IN_MINUTES),
            eq(TimeUnit.MINUTES));
  }

  @Test
  public void testReplenishPermitsScheduledRunnableIsWorking() {
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduledExecutorMock)
        .scheduleAtFixedRate(
            runnableCaptor.capture(),
            eq(DEFAULT_TIME_LAPSE_IN_MINUTES),
            eq(DEFAULT_TIME_LAPSE_IN_MINUTES),
            eq(TimeUnit.MINUTES));

    // Use all permits
    testAcquire();

    // force execution of the runnable that replenish the permits
    runnableCaptor.getValue().run();

    assertThat(limiter.availablePermits()).isEqualTo(RATE);
  }
}
