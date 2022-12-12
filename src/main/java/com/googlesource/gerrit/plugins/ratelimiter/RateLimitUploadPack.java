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

import static com.googlesource.gerrit.plugins.ratelimiter.Configuration.RATE_LIMIT_TOKEN;
import static com.googlesource.gerrit.plugins.ratelimiter.Module.UPLOAD_PACK_PER_HOUR;

import com.google.common.cache.LoadingCache;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.validators.UploadValidationListener;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UploadPack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class RateLimitUploadPack implements UploadValidationListener {

  private static final Logger log = LoggerFactory.getLogger(RateLimitUploadPack.class);
  private final Provider<CurrentUser> user;
  private final LoadingCache<String, RateLimiter> uploadPackPerHour;
  private final String limitExceededMsgFormat;
  private final Module.RateLimiterLoader rateLimiterLoader;
  private final Configuration configuration;

  @Inject
  RateLimitUploadPack(
      Provider<CurrentUser> user,
      @Named(UPLOAD_PACK_PER_HOUR) LoadingCache<String, RateLimiter> uploadPackPerHour,
      Configuration configuration,
      Module.RateLimiterLoader rateLimiterLoader) {
    this.user = user;
    this.uploadPackPerHour = uploadPackPerHour;
    limitExceededMsgFormat =
        configuration.getRateLimitExceededMsg().replace(RATE_LIMIT_TOKEN, "{0,number,##.##}");
    this.rateLimiterLoader = rateLimiterLoader;
    this.configuration = configuration;
  }

  @Override
  public void onBeginNegotiate(
      Repository repository,
      Project project,
      String remoteHost,
      UploadPack up,
      Collection<? extends ObjectId> wants,
      int cntOffered)
      throws ValidationException {
    String key;
    CurrentUser u = user.get();
    if (u.isIdentifiedUser()) {
      key = Integer.toString(u.asIdentifiedUser().getAccountId().get());
    } else {
      key = remoteHost;
    }

    try {
      RateLimiter limiter = uploadPackPerHour.get(key);
      if (!limiter.acquirePermit()) {
        throw new RateLimitException(
            MessageFormat.format(limitExceededMsgFormat, limiter.permitsPerHour()));
      }
    } catch (ExecutionException e) {
      log.warn("Cannot get rate limits for {}: {}", key, e);
    }
  }

  void refresh(ProjectConfig newCfg, ProjectConfig oldCfg) {
    configuration.refreshTable(newCfg, oldCfg);
    refreshCache();
  }

  private void refreshCache() {
    uploadPackPerHour
        .asMap()
        .keySet()
        .parallelStream()
        .filter(
            key -> {
              try {
                return !rateLimiterLoader.isValidKey(key, uploadPackPerHour.get(key));
              } catch (ExecutionException e) {
                log.warn("Cannot get rate limits for {}: {}", key, e);
              }
              return false;
            })
        .forEach(
            key -> {
              try {
                uploadPackPerHour.get(key).close();
                uploadPackPerHour.invalidate(key);
              } catch (ExecutionException e) {
                throw new RuntimeException(e);
              }
            });
  }

  @Override
  public void onPreUpload(
      Repository repository,
      Project project,
      String remoteHost,
      UploadPack up,
      Collection<? extends ObjectId> wants,
      Collection<? extends ObjectId> haves)
      throws ValidationException {
    // nothing to do here, only onBeginNegotiate is needed
  }
}
