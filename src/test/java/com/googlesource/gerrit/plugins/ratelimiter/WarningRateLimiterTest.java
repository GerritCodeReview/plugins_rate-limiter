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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.server.IdentifiedUser;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class WarningRateLimiterTest {

  private static final int RATE = 1000;
  private static final int WARN_RATE = 900;
  private IdentifiedUser identifiedUser = mock(IdentifiedUser.class);
  private WarningRateLimiter warningLimiter;
  private ScheduledExecutorService scheduledExecutorMock;
  private UserResolver userResolver = mock(UserResolver.class);
  private RateLimitReachedSender.Factory rateLimitReachedSenderFactory =
      mock(RateLimitReachedSender.Factory.class);
  private RateLimitReachedSender sender = mock(RateLimitReachedSender.class);

  @Before
  public void setUp() {
    scheduledExecutorMock = mock(ScheduledExecutorService.class);

    PeriodicRateLimiter limiter =
        spy(
            new PeriodicRateLimiter(
                scheduledExecutorMock, RATE, DEFAULT_TIME_LAPSE_IN_MINUTES, "Any Type"));
    doReturn(1L).when(limiter).remainingTime(any(TimeUnit.class));

    warningLimiter =
        new WarningRateLimiter(
            userResolver,
            rateLimitReachedSenderFactory,
            limiter,
            "dummy",
            WARN_RATE,
            DEFAULT_TIME_LAPSE_IN_MINUTES);
  }

  @Test
  public void testGetRatePerHour() {
    assertThat(warningLimiter.permitsPerHour()).isEqualTo(RATE);
  }

  @Test
  public void testAcquireAll() {
    assertThat(warningLimiter.availablePermits()).isEqualTo(RATE);

    for (int permitNum = 1; permitNum <= RATE; permitNum++) {
      checkGetPermitPasses(warningLimiter, permitNum);
    }
    checkGetPermitFails(warningLimiter);
  }

  @Test
  public void testAcquireWarning() throws EmailException {
    when(userResolver.getIdentifiedUser(any())).thenReturn(Optional.ofNullable(identifiedUser));
    when(rateLimitReachedSenderFactory.create(any(), any(), anyBoolean())).thenReturn(sender);
    assertThat(warningLimiter.availablePermits()).isEqualTo(RATE);

    for (int permitNum = 1; permitNum < WARN_RATE; permitNum++) {
      checkGetPermitPasses(warningLimiter, permitNum);
    }
    // Check that the warning has not yet been triggered
    assertThat(warningLimiter.getWarningFlagState()).isFalse();

    // Trigger the warning
    assertThat(warningLimiter.acquirePermit()).isTrue();
    verify(sender, times(1)).send();
    assertThat(warningLimiter.getWarningFlagState()).isTrue();

    for (int permitNum = WARN_RATE + 1; permitNum <= RATE; permitNum++) {
      checkGetPermitPasses(warningLimiter, permitNum);
    }
    checkGetPermitFails(warningLimiter);
    verify(sender, times(2)).send();
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

    replenishPermits(warningLimiter, runnableCaptor);
    testAcquireAll();

    // Check the available permits are used up
    assertThat(warningLimiter.availablePermits()).isEqualTo(0);

    replenishPermits(warningLimiter, runnableCaptor);
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
