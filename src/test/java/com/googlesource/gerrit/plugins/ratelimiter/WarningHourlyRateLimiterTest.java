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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class WarningHourlyRateLimiterTest {

  private static final int RATE = 1000;
  private static final int WARN_RATE = 900;
  private WarningHourlyRateLimiter warningLimiter1;
  private WarningHourlyRateLimiter warningLimiter2;
  private ScheduledExecutorService scheduledExecutorMock1;
  private UserResolver userResolver = mock(UserResolver.class);

  @Before
  public void setUp() {
    scheduledExecutorMock1 = mock(ScheduledExecutorService.class);

    ScheduledExecutorService scheduledExecutorMock2 = mock(ScheduledExecutorService.class);

    HourlyRateLimiter limiter1 = spy(new HourlyRateLimiter(scheduledExecutorMock1, RATE));
    doReturn(1L).when(limiter1).remainingTime(any(TimeUnit.class));

    HourlyRateLimiter limiter2 = spy(new HourlyRateLimiter(scheduledExecutorMock2, RATE));
    doReturn(1L).when(limiter2).remainingTime(any(TimeUnit.class));

    warningLimiter1 = new WarningHourlyRateLimiter(userResolver, limiter1, "dummy", WARN_RATE);
    warningLimiter2 = new WarningHourlyRateLimiter(userResolver, limiter2, "dummy2", WARN_RATE);
  }

  @Test
  public void testGetRatePerHour() {
    assertThat(warningLimiter1.permitsPerHour()).isEqualTo(RATE);
  }

  @Test
  public void testAcquireAll() {
    assertThat(warningLimiter1.availablePermits()).isEqualTo(RATE);

    for (int permitNum = 1; permitNum <= RATE; permitNum++) {
      checkGetPermitPasses(warningLimiter1, permitNum);
    }
    checkGetPermitFails(warningLimiter1);
  }

  @Test
  public void testAcquireWarning() {
    assertThat(warningLimiter2.availablePermits()).isEqualTo(RATE);

    for (int permitNum = 1; permitNum < WARN_RATE; permitNum++) {
      checkGetPermitPasses(warningLimiter2, permitNum);
    }
    // Check that the warning has not yet been triggered
    assertThat(warningLimiter2.getWarningFlagState()).isFalse();

    // Trigger the warning
    assertThat(warningLimiter2.acquirePermit()).isTrue();
    assertThat(warningLimiter2.getWarningFlagState()).isTrue();

    for (int permitNum = WARN_RATE + 1; permitNum <= RATE; permitNum++) {
      checkGetPermitPasses(warningLimiter2, permitNum);
    }
    checkGetPermitFails(warningLimiter2);
  }

  @Test
  public void testReplenishPermitsIsScheduled() {
    verify(scheduledExecutorMock1).scheduleAtFixedRate(any(), eq(1L), eq(1L), eq(TimeUnit.HOURS));
  }

  @Test
  public void testReplenishPermitsScheduledRunnableIsWorking() {
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduledExecutorMock1)
        .scheduleAtFixedRate(runnableCaptor.capture(), eq(1L), eq(1L), eq(TimeUnit.HOURS));

    replenishPermits(warningLimiter1, runnableCaptor);
    testAcquireAll();

    // Check the available permits are used up
    assertThat(warningLimiter1.availablePermits()).isEqualTo(0);

    replenishPermits(warningLimiter1, runnableCaptor);
  }

  private void checkGetPermitPasses(RateLimiter rateLimiter, int permitNum) {
    assertThat(rateLimiter.acquirePermit()).isTrue();
    assertThat(rateLimiter.availablePermits()).isEqualTo(RATE - permitNum);
  }

  private void checkGetPermitFails(RateLimiter rateLimiter) {
    assertThat(rateLimiter.acquirePermit()).isFalse();
    assertThat(rateLimiter.availablePermits()).isEqualTo(0);
  }

  private void replenishPermits(RateLimiter rateLimiter, ArgumentCaptor<Runnable> task) {
    task.getValue().run();
    assertThat(rateLimiter.availablePermits()).isEqualTo(RATE);
  }
}
