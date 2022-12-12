// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
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
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.concurrent.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RateLimiterLoaderTest {
  private final String KEY = "10000";
  private final int INITIAL_RATE_LIMIT = 1;
  private final int INITIAL_WARNING = 1;
  private final int NEW_RATE_LIMIT = 10;

  private final int NEW_TIME_LAPSE = 10;
  private final int NEW_WARNINIG = 10;
  private Module.RateLimiterLoader loader;

  @Mock private RateLimitFinder finder;
  @Mock private Configuration configuration;
  @Mock private PeriodicRateLimiter.Factory periodicRateLimiterFactory;
  @Mock private WarningRateLimiter.Factory warningRateLimiterFactory;
  @Mock private WarningUnlimitedRateLimiter.Factory warningUnlimitedRateLimiterFactory;
  @Mock private UserResolver userResolver;
  @Mock private ScheduledExecutorService scheduledExecutorService;
  @Mock private ScheduledFuture<?> replenishTask;
  @Mock private PeriodicRateLimiter periodicRateLimiterMock;
  @Mock private RateLimitReachedSender.Factory rateLimitReachedSenderFactoryMock;
  private PeriodicRateLimiter DefaultRateLimiter;
  private WarningUnlimitedRateLimiter DefaultWarningUnlimitedRateLimiter;
  private WarningRateLimiter DefaultWarningRateLimiter;

  Optional<RateLimit> limitInTable;
  Optional<RateLimit> warnInTable;
  Optional<RateLimit> timeLapseInTable;

  @Before
  public void setUp() {
    loader =
        new Module.RateLimiterLoader(
            finder,
            configuration,
            periodicRateLimiterFactory,
            warningRateLimiterFactory,
            warningUnlimitedRateLimiterFactory);
    DefaultRateLimiter =
        createMockPeriodicRateLimiter(INITIAL_RATE_LIMIT, DEFAULT_TIME_LAPSE_IN_MINUTES);
    DefaultWarningRateLimiter = createMockWarningRateLimiter(INITIAL_WARNING);
    DefaultWarningUnlimitedRateLimiter = createMockWarningUnlimitedRateLimiter(INITIAL_WARNING);
    DefaultRateLimiter.setReplenishTask(replenishTask);
  }

  @Test
  public void reactOnChangeOfTimeLapseInAllProject() {
    limitInTable =
        Optional.of(new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, INITIAL_RATE_LIMIT));
    warnInTable = Optional.empty();
    timeLapseInTable =
        Optional.of(new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, NEW_TIME_LAPSE));

    setPrecondition(limitInTable, warnInTable, timeLapseInTable);
    when(periodicRateLimiterFactory.create(
            limitInTable.get().getRatePerHour(),
            timeLapseInTable.get().getRatePerHour(),
            RateLimitType.UPLOAD_PACK_PER_HOUR.getLimitType()))
        .thenReturn(periodicRateLimiterMock);

    RateLimiter newLimiter = DefaultRateLimiter;
    newLimiter = loader.loadLatestConfiguration(KEY, newLimiter);
    assertThat(newLimiter).isEqualTo(periodicRateLimiterMock);
  }

  @Test
  public void reactOnChangeOfRateLimitInAllProject() {
    limitInTable = Optional.of(new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, NEW_RATE_LIMIT));
    warnInTable = Optional.empty();
    timeLapseInTable =
        Optional.of(
            new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, DEFAULT_TIME_LAPSE_IN_MINUTES));

    setPrecondition(limitInTable, warnInTable, timeLapseInTable);
    when(periodicRateLimiterFactory.create(
            limitInTable.get().getRatePerHour(),
            timeLapseInTable.get().getRatePerHour(),
            RateLimitType.UPLOAD_PACK_PER_HOUR.getLimitType()))
        .thenReturn(periodicRateLimiterMock);

    RateLimiter newLimiter = DefaultRateLimiter;
    newLimiter = loader.loadLatestConfiguration(KEY, newLimiter);
    assertThat(newLimiter).isEqualTo(periodicRateLimiterMock);
  }

  @Test
  public void changeWarningOfWarningRateLimiter() {
    limitInTable =
        Optional.of(new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, INITIAL_RATE_LIMIT));
    warnInTable = Optional.of(new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, NEW_WARNINIG));
    timeLapseInTable =
        Optional.of(
            new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, DEFAULT_TIME_LAPSE_IN_MINUTES));

    setPrecondition(limitInTable, warnInTable, timeLapseInTable);

    when(periodicRateLimiterFactory.create(
            limitInTable.get().getRatePerHour(),
            timeLapseInTable.get().getRatePerHour(),
            RateLimitType.UPLOAD_PACK_PER_HOUR.getLimitType()))
        .thenReturn(periodicRateLimiterMock);
    when(warningRateLimiterFactory.create(
            periodicRateLimiterMock, KEY, warnInTable.get().getRatePerHour()))
        .thenReturn(createMockWarningRateLimiter(warnInTable.get().getRatePerHour()));

    RateLimiter newLimiter = DefaultWarningRateLimiter;
    assertThat(newLimiter.getWarnLimit().get()).isEqualTo(INITIAL_WARNING);
    newLimiter = loader.loadLatestConfiguration(KEY, newLimiter);
    assertThat(newLimiter.getWarnLimit().get()).isEqualTo(NEW_WARNINIG);
  }

  @Test
  public void DoNotCreateNewLimitIfSameConfig() {
    limitInTable =
        Optional.of(new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, INITIAL_RATE_LIMIT));
    warnInTable = Optional.empty();
    timeLapseInTable =
        Optional.of(
            new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, DEFAULT_TIME_LAPSE_IN_MINUTES));

    setPrecondition(limitInTable, warnInTable, timeLapseInTable);

    verify(periodicRateLimiterFactory, never())
        .create(any(Integer.class), any(Integer.class), any(String.class));
    RateLimiter newLimiter = DefaultRateLimiter;
    newLimiter = loader.loadLatestConfiguration(KEY, newLimiter);
    assertThat(newLimiter).isEqualTo(DefaultRateLimiter);
  }

  @Test
  public void changeWarningOfUnlimitedWarningRateLimiter() {
    limitInTable = Optional.empty();
    warnInTable = Optional.of(new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, NEW_WARNINIG));
    timeLapseInTable =
        Optional.of(
            new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, DEFAULT_TIME_LAPSE_IN_MINUTES));

    setPrecondition(limitInTable, warnInTable, timeLapseInTable);
    when(periodicRateLimiterFactory.create(
            Module.DEFAULT_LIMIT,
            timeLapseInTable.get().getRatePerHour(),
            RateLimitType.UPLOAD_PACK_PER_HOUR.getLimitType()))
        .thenReturn(periodicRateLimiterMock);
    when(warningUnlimitedRateLimiterFactory.create(
            periodicRateLimiterMock, KEY, warnInTable.get().getRatePerHour()))
        .thenReturn(createMockWarningUnlimitedRateLimiter(warnInTable.get().getRatePerHour()));

    RateLimiter newLimiter = DefaultWarningUnlimitedRateLimiter;
    assertThat(newLimiter.getWarnLimit().get()).isEqualTo(INITIAL_WARNING);
    newLimiter = loader.loadLatestConfiguration(KEY, newLimiter);
    assertThat(newLimiter.getWarnLimit().get()).isEqualTo(NEW_WARNINIG);
  }

  @Test
  public void changeToWarningRateLimiter() {
    limitInTable =
        Optional.of(new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, INITIAL_RATE_LIMIT));
    warnInTable = Optional.of(new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, INITIAL_WARNING));
    timeLapseInTable =
        Optional.of(
            new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, DEFAULT_TIME_LAPSE_IN_MINUTES));

    setPrecondition(limitInTable, warnInTable, timeLapseInTable);
    when(periodicRateLimiterFactory.create(
            limitInTable.get().getRatePerHour(),
            DEFAULT_TIME_LAPSE_IN_MINUTES,
            RateLimitType.UPLOAD_PACK_PER_HOUR.getLimitType()))
        .thenReturn(periodicRateLimiterMock);
    when(warningRateLimiterFactory.create(
            any(PeriodicRateLimiter.class), any(String.class), any(Integer.class)))
        .thenReturn(createMockWarningRateLimiter(warnInTable.get().getRatePerHour()));

    assertThat(loader.loadLatestConfiguration(KEY, DefaultRateLimiter))
        .isInstanceOf(WarningRateLimiter.class);
    assertThat(loader.loadLatestConfiguration(KEY, UnlimitedRateLimiter.INSTANCE))
        .isInstanceOf(WarningRateLimiter.class);
    assertThat(loader.loadLatestConfiguration(KEY, DefaultWarningRateLimiter))
        .isInstanceOf(WarningRateLimiter.class);
    assertThat(loader.loadLatestConfiguration(KEY, DefaultWarningUnlimitedRateLimiter))
        .isInstanceOf(WarningRateLimiter.class);
  }

  @Test
  public void changeToUnlimitedWarningRateLimiter() {
    limitInTable = Optional.empty();
    warnInTable = Optional.of(new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, 10));
    timeLapseInTable =
        Optional.of(
            new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, DEFAULT_TIME_LAPSE_IN_MINUTES));

    setPrecondition(limitInTable, warnInTable, timeLapseInTable);
    when(periodicRateLimiterFactory.create(
            Module.DEFAULT_LIMIT,
            DEFAULT_TIME_LAPSE_IN_MINUTES,
            RateLimitType.UPLOAD_PACK_PER_HOUR.getLimitType()))
        .thenReturn(periodicRateLimiterMock);
    when(warningUnlimitedRateLimiterFactory.create(
            any(PeriodicRateLimiter.class), any(String.class), any(Integer.class)))
        .thenReturn(createMockWarningUnlimitedRateLimiter(warnInTable.get().getRatePerHour()));

    assertThat(loader.loadLatestConfiguration(KEY, DefaultRateLimiter))
        .isInstanceOf(WarningUnlimitedRateLimiter.class);
    assertThat(loader.loadLatestConfiguration(KEY, UnlimitedRateLimiter.INSTANCE))
        .isInstanceOf(WarningUnlimitedRateLimiter.class);
    assertThat(loader.loadLatestConfiguration(KEY, DefaultWarningRateLimiter))
        .isInstanceOf(WarningUnlimitedRateLimiter.class);
    assertThat(loader.loadLatestConfiguration(KEY, DefaultWarningUnlimitedRateLimiter))
        .isInstanceOf(WarningUnlimitedRateLimiter.class);
  }

  @Test
  public void changeToRateLimiter() {
    limitInTable = Optional.of(new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, 10));
    warnInTable = Optional.empty();
    timeLapseInTable =
        Optional.of(
            new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, DEFAULT_TIME_LAPSE_IN_MINUTES));

    setPrecondition(limitInTable, warnInTable, timeLapseInTable);
    when(periodicRateLimiterFactory.create(any(Integer.class), any(Long.class), any(String.class)))
        .thenReturn(periodicRateLimiterMock);

    assertThat(loader.loadLatestConfiguration(KEY, DefaultRateLimiter))
        .isInstanceOf(PeriodicRateLimiter.class);
    assertThat(loader.loadLatestConfiguration(KEY, UnlimitedRateLimiter.INSTANCE))
        .isInstanceOf(PeriodicRateLimiter.class);
    assertThat(loader.loadLatestConfiguration(KEY, DefaultWarningRateLimiter))
        .isInstanceOf(PeriodicRateLimiter.class);
    assertThat(loader.loadLatestConfiguration(KEY, DefaultWarningUnlimitedRateLimiter))
        .isInstanceOf(PeriodicRateLimiter.class);
  }

  @Test
  public void changeToUnlimitedRateLimiter() {
    limitInTable = Optional.empty();
    warnInTable = Optional.empty();
    timeLapseInTable =
        Optional.of(
            new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, DEFAULT_TIME_LAPSE_IN_MINUTES));

    setPrecondition(limitInTable, warnInTable, timeLapseInTable);

    assertThat(loader.loadLatestConfiguration(KEY, DefaultRateLimiter))
        .isInstanceOf(UnlimitedRateLimiter.class);
    assertThat(loader.loadLatestConfiguration(KEY, UnlimitedRateLimiter.INSTANCE))
        .isInstanceOf(UnlimitedRateLimiter.class);
    assertThat(loader.loadLatestConfiguration(KEY, DefaultWarningRateLimiter))
        .isInstanceOf(UnlimitedRateLimiter.class);
    assertThat(loader.loadLatestConfiguration(KEY, DefaultWarningUnlimitedRateLimiter))
        .isInstanceOf(UnlimitedRateLimiter.class);
  }

  private void setPrecondition(
      Optional<RateLimit> limitInTable,
      Optional<RateLimit> warnInTable,
      Optional<RateLimit> timeLapseInTable) {
    when(finder.find(RateLimitType.UPLOAD_PACK_PER_HOUR, KEY)).thenReturn(limitInTable);
    when(finder.find(RateLimitType.UPLOAD_PACK_PER_HOUR_WARN, KEY)).thenReturn(warnInTable);
    when(finder.find(RateLimitType.TIME_LAPSE_IN_MINUTES, KEY)).thenReturn(timeLapseInTable);
  }

  private WarningUnlimitedRateLimiter createMockWarningUnlimitedRateLimiter(int warnLimit) {
    return new WarningUnlimitedRateLimiter(userResolver, periodicRateLimiterMock, KEY, warnLimit);
  }

  private WarningRateLimiter createMockWarningRateLimiter(int warnLimit) {
    return new WarningRateLimiter(userResolver, rateLimitReachedSenderFactoryMock, periodicRateLimiterMock, KEY, warnLimit);
  }

  private PeriodicRateLimiter createMockPeriodicRateLimiter(int permits, int timeLapse) {
    return new PeriodicRateLimiter(
        scheduledExecutorService,
        permits,
        timeLapse,
        RateLimitType.UPLOAD_PACK_PER_HOUR.getLimitType());
  }
}
