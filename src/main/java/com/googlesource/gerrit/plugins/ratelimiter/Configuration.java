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

import com.google.common.collect.ArrayTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupDescription.Basic;
import com.google.gerrit.entities.ImmutableConfig;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.GerritIsReplica;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class Configuration {

  static final String RATE_LIMIT_TOKEN = "${rateLimit}";
  private static final Logger log = LoggerFactory.getLogger(RateLimitUploadPack.class);
  private static final String GROUP_SECTION = "group";
  private static final String DEFAULT_UPLOADPACK_LIMIT_EXCEEDED_MSG =
      "Exceeded rate limit of " + RATE_LIMIT_TOKEN + " fetch requests/hour";
  private static final String RATE_LIMITER_CONFIG = "rate-limiter.config";
  private final Boolean isReplica;
  private Table<RateLimitType, AccountGroup.UUID, RateLimit> rateLimits;
  private List<AccountGroup.UUID> recipients;
  private String rateLimitExceededMsg;
  private final PluginConfigFactory pluginConfigFactory;
  private final GroupResolver groupsCollection;
  private final String pluginName;
  private final Config defaultRateLimiterConfig;

  @Inject
  Configuration(
      AllProjectsName allProjectsName,
      PluginConfigFactory pluginConfigFactory,
      @PluginName String pluginName,
      @GerritIsReplica Boolean isReplica,
      GroupResolver groupsCollection) {
    this.pluginConfigFactory = pluginConfigFactory;
    this.groupsCollection = groupsCollection;
    this.pluginName = pluginName;
    this.isReplica = isReplica;
    this.defaultRateLimiterConfig = pluginConfigFactory.getGlobalPluginConfig(pluginName);
    initConfig(loadConfig(allProjectsName.get()));
  }

  private void initConfig(Config config) {
    recipients = parseUserGroupsForEmailNotification(config, groupsCollection);
    rateLimitExceededMsg = parseLimitExceededMsg(config);
    Map<String, AccountGroup.UUID> groups = getResolvedGroups(config, groupsCollection);
    parseAllGroupsRateLimits(config, groups);
  }

  private Config loadConfig(String projectName) {
    if (isReplica) {
      return defaultRateLimiterConfig;
    }
    try {
      Config config =
          pluginConfigFactory.getProjectPluginConfigWithInheritance(
              Project.NameKey.parse(projectName), pluginName);
      if (config == null || config.getSubsections(GROUP_SECTION).isEmpty()) {
        config = defaultRateLimiterConfig;
      }
      return config;
    } catch (NoSuchProjectException e) {
      log.warn("No project {} found", projectName);
      return defaultRateLimiterConfig;
    }
  }

  private List<AccountGroup.UUID> parseUserGroupsForEmailNotification(
      Config config, GroupResolver groupsCollection) {
    String sendEmailSection = "sendemail";
    String recipients = "recipients";
    Optional<String> rowValueOptional =
        Optional.ofNullable(config.getString(sendEmailSection, null, recipients));
    return rowValueOptional
        .map(s -> resolveGroupsFromParsedValue(s, groupsCollection))
        .orElseGet(ImmutableList::of);
  }

  private List<AccountGroup.UUID> resolveGroupsFromParsedValue(
      String configValue, GroupResolver groupsCollection) {
    List<AccountGroup.UUID> groups = new CopyOnWriteArrayList<>();
    String[] groupNames = configValue.split("\\s*,\\s*");
    for (String groupName : groupNames) {
      Basic basic = groupsCollection.parseId(groupName);
      if (basic != null) {
        groups.add(basic.getGroupUUID());
      }
    }
    return groups;
  }

  void refreshTable(ProjectConfig newCfg, ProjectConfig oldCfg) {
    if (oldCfg != null) {
      try {
        ImmutableMap<String, String> oldCacheable = oldCfg.getCacheable().getProjectLevelConfigs();
        String oldStringConfig = oldCacheable.getOrDefault(RATE_LIMITER_CONFIG, "");
        ImmutableMap<String, String> newCacheable = newCfg.getCacheable().getProjectLevelConfigs();
        String newStringConfig = newCacheable.getOrDefault(RATE_LIMITER_CONFIG, "");
        if (oldStringConfig.equals(newStringConfig)) {
          return;
        }
        if (newStringConfig != "") {
          Config newConfig = ImmutableConfig.parse(newStringConfig).mutableCopy();
          initConfig(newConfig);
        } else {
          initConfig(defaultRateLimiterConfig);
        }
      } catch (ConfigInvalidException e) {
        log.warn("Invalid Configuration");
      }
    }
  }

  private void parseAllGroupsRateLimits(Config config, Map<String, AccountGroup.UUID> groups) {
    if (groups.size() == 0) {
      log.warn("No configuration found");
      rateLimits = null;
      return;
    }
    rateLimits = ArrayTable.create(Arrays.asList(RateLimitType.values()), groups.values());
    for (Entry<String, AccountGroup.UUID> group : groups.entrySet()) {
      parseGroupRateLimits(config, group.getKey(), group.getValue());
    }
  }

  private Map<String, AccountGroup.UUID> getResolvedGroups(
      Config config, GroupResolver groupsCollection) {
    LinkedHashMap<String, AccountGroup.UUID> groups = new LinkedHashMap<>();
    for (String groupName : config.getSubsections(GROUP_SECTION)) {
      Basic basic = groupsCollection.parseId(groupName);
      if (basic != null) {
        groups.put(groupName, basic.getGroupUUID());
      }
    }
    return groups;
  }

  private void parseGroupRateLimits(Config config, String groupName, AccountGroup.UUID groupUUID)
      throws ProvisionException {
    for (String typeName : config.getNames(GROUP_SECTION, groupName, true)) {
      RateLimitType rateLimitType = RateLimitType.from(typeName);
      if (rateLimitType != null) {
        rateLimits.put(rateLimitType, groupUUID, parseRateLimit(config, groupName, rateLimitType));
      } else {
        throw new ProvisionException(
            String.format("Invalid configuration, unsupported rate limit type: %s", typeName));
      }
    }
  }

  private static RateLimit parseRateLimit(Config c, String groupName, RateLimitType rateLimitType) {
    String value = c.getString(GROUP_SECTION, groupName, rateLimitType.toString());
    try {
      return new RateLimit(rateLimitType, Integer.parseInt(value));
    } catch (NumberFormatException e) {
      throw new ProvisionException(
          String.format(
              "Invalid configuration, 'rate limit value '%s' for '%s.%s.%s' is not a valid number",
              value, GROUP_SECTION, groupName, rateLimitType.toString()));
    }
  }

  private static String parseLimitExceededMsg(Config config) {
    String msg = config.getString("configuration", null, "uploadpackLimitExceededMsg");
    return (msg != null) ? msg : DEFAULT_UPLOADPACK_LIMIT_EXCEEDED_MSG;
  }

  String getRateLimitExceededMsg() {
    return rateLimitExceededMsg;
  }

  /**
   * @param rateLimitType type of rate limit
   * @return map of rate limits per group uuid
   */
  Map<AccountGroup.UUID, RateLimit> getRateLimits(RateLimitType rateLimitType) {
    return rateLimits != null ? rateLimits.row(rateLimitType) : ImmutableMap.of();
  }

  List<AccountGroup.UUID> getRecipients() {
    return !recipients.isEmpty() ? recipients : ImmutableList.of();
  }

  static boolean isSameRateLimitType(
      RateLimiter limiter, Optional<RateLimit> limit, Optional<RateLimit> warn) {
    if (limit.isPresent() && warn.isPresent()) {
      return limiter instanceof WarningRateLimiter;
    }
    if (limit.isEmpty() && warn.isPresent()) {
      return limiter instanceof WarningUnlimitedRateLimiter;
    }
    if (limit.isPresent()) {
      return limiter instanceof PeriodicRateLimiter;
    } else {
      return limiter instanceof UnlimitedRateLimiter;
    }
  }

  public static boolean validTimeLapse(Optional<RateLimit> timeLapse, int defaultTimeLapce) {
    if (timeLapse.isPresent()) {
      long providedTimeLapse = timeLapse.get().getRatePerHour();
      if (providedTimeLapse > 0 && providedTimeLapse <= defaultTimeLapce) {
        return true;
      }
      log.warn(
          "The time lapse is set to the default {} minutes, as the configured value is invalid.",
          defaultTimeLapce);
    } else {
      log.warn(
          "The time lapse is set to the default {} minutes, as the configured value is not present.",
          defaultTimeLapce);
    }
    return false;
  }
}
