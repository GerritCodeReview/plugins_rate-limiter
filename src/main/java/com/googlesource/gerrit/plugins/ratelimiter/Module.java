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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.git.validators.UploadValidationListener;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.name.Named;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class Module extends AbstractModule {
  static final String UPLOAD_PACK_PER_HOUR = "upload_pack_per_hour";

  @Override
  protected void configure() {
    DynamicSet.bind(binder(), UploadValidationListener.class).to(RateLimitUploadPack.class);
    bind(Configuration.class).asEagerSingleton();
    bind(ScheduledExecutorService.class)
        .annotatedWith(RateLimitExecutor.class)
        .toProvider(RateLimitExecutorProvider.class);
    bind(LifecycleListener.class)
        .annotatedWith(UniqueAnnotations.create())
        .to(RateLimitExecutorProvider.class);
    bind(LifecycleListener.class)
        .annotatedWith(UniqueAnnotations.create())
        .to(RateLimiterStatsLog.class);
    install(new FactoryModuleBuilder().build(CustomRateLimiter.Factory.class));
    install(new FactoryModuleBuilder().build(WarningRateLimiter.Factory.class));
    install(new FactoryModuleBuilder().build(WarningUnlimitedRateLimiter.Factory.class));
  }

  @Provides
  @Named(UPLOAD_PACK_PER_HOUR)
  @Singleton
  LoadingCache<String, RateLimiter> getUploadPackPerHourCache(Provider<RateLimiterLoader> loader) {
    return CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.HOURS)
        .removalListener(
            (RemovalListener<String, RateLimiter>)
                removalNotification -> removalNotification.getValue().close())
        .build(loader.get());
  }

  private static class RateLimiterLoader extends CacheLoader<String, RateLimiter> {
    private final RateLimitFinder finder;
    private final CustomRateLimiter.Factory customRateLimiterFactory;
    private final WarningRateLimiter.Factory warningRateLimiterFactory;
    private final WarningUnlimitedRateLimiter.Factory warningUnlimitedRateLimiterFactory;

    @Inject
    RateLimiterLoader(
        RateLimitFinder finder,
        CustomRateLimiter.Factory customRateLimiterFactory,
        WarningRateLimiter.Factory warningRateLimiterFactory,
        WarningUnlimitedRateLimiter.Factory warningUnlimitedRateLimiterFactory) {
      this.finder = finder;
      this.customRateLimiterFactory = customRateLimiterFactory;
      this.warningRateLimiterFactory = warningRateLimiterFactory;
      this.warningUnlimitedRateLimiterFactory = warningUnlimitedRateLimiterFactory;
    }

    @Override
    public RateLimiter load(String key) {
      Optional<RateLimit> limit = finder.find(RateLimitType.UPLOAD_PACK_PER_HOUR, key);
      Optional<RateLimit> warn = finder.find(RateLimitType.UPLOAD_PACK_PER_HOUR_WARN, key);
      Optional<RateLimit> timeLapse = finder.find(RateLimitType.TIME_LAPSE_IN_MINUTES, key);
      if (!limit.isPresent() && !warn.isPresent()) {
        return UnlimitedRateLimiter.INSTANCE;
      }

      // In the case that there is a warning but no limit
      Integer myLimit = Integer.MAX_VALUE;
      if (limit.isPresent()) {
        myLimit = limit.get().getRatePerHour();
      }

      long time = CustomRateLimiter.DEFAULT_HOUR;
      if (timeLapse.isPresent() && timeLapse.get().getRatePerHour() < time) {
        time = timeLapse.get().getRatePerHour();
      }
      RateLimiter rateLimiter = customRateLimiterFactory.create(myLimit, time);

      if (warn.isPresent()) {
        if (limit.isPresent()) {
          return warningRateLimiterFactory.create(
              rateLimiter, key, warn.get().getRatePerHour(), time);
        }
        return warningUnlimitedRateLimiterFactory.create(
            rateLimiter, key, warn.get().getRatePerHour(), time);
      }
      return rateLimiter;
    }
  }
}
