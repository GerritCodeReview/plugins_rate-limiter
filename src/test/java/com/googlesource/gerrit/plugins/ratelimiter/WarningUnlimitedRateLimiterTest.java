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

public class WarningUnlimitedRateLimiterTest {

  private static final int RATE = 1000;
  private static final int WARN_RATE = 900;
  private WarningUnlimitedRateLimiter warningUnlimitedLimiter;
  private ScheduledExecutorService scheduledExecutorMock;
  private UserResolver userResolver = mock(UserResolver.class);

  @Before
  public void setUp() {
    scheduledExecutorMock = mock(ScheduledExecutorService.class);
    PeriodicRateLimiter limiter =
        new PeriodicRateLimiter(scheduledExecutorMock, RATE, DEFAULT_TIME_LAPSE_IN_MINUTES, "Any Type");
    warningUnlimitedLimiter =
        new WarningUnlimitedRateLimiter(
            userResolver, limiter, "dummy", WARN_RATE, DEFAULT_TIME_LAPSE_IN_MINUTES);
  }

  @Test
  public void testGetRatePerHour() {
    assertThat(warningUnlimitedLimiter.maxPermits()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  public void testTriggerWarning() {
    assertThat(warningUnlimitedLimiter.availablePermits()).isEqualTo(Integer.MAX_VALUE);

    for (int i = 1; i < WARN_RATE; i++) {
      assertThat(warningUnlimitedLimiter.acquirePermit()).isTrue();
      assertThat(warningUnlimitedLimiter.availablePermits()).isEqualTo(Integer.MAX_VALUE);
    }
    // Check that the warning has not yet been triggered
    assertThat(warningUnlimitedLimiter.getWarningFlagState()).isFalse();

    // Trigger the warning
    assertThat(warningUnlimitedLimiter.acquirePermit()).isTrue();
    assertThat(warningUnlimitedLimiter.getWarningFlagState()).isTrue();

    // Check there still is no limit
    assertThat(warningUnlimitedLimiter.availablePermits()).isEqualTo(Integer.MAX_VALUE);
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

    testTriggerWarning();

    // Check there is still an unlimited number of permits
    assertThat(warningUnlimitedLimiter.availablePermits()).isEqualTo(Integer.MAX_VALUE);

    // Replenishes the permits and clears count for warning
    runnableCaptor.getValue().run();
    assertThat(warningUnlimitedLimiter.availablePermits()).isEqualTo(Integer.MAX_VALUE);
    assertThat(warningUnlimitedLimiter.usedPermits()).isEqualTo(0);
  }
}
