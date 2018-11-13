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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class Configuration {

  private static final Logger log = LoggerFactory.getLogger(Configuration.class);

  static final String RATE_LIMIT_TOKEN = "${rateLimit}";
  private static final String GROUP_SECTION = "group";
  private static final String DEFAULT_UPLOADPACK_LIMIT_EXCEEDED_MSG =
      "Exceeded rate limit of " + RATE_LIMIT_TOKEN + " fetch requests/hour";

  private Table<RateLimitType, AccountGroup.UUID, RateLimit> rateLimits;
  private final String rateLimitExceededMsg;

  @Inject
  Configuration(
      PluginConfigFactory pluginConfigFactory,
      @PluginName String pluginName,
      GroupsCollection groupsCollection) {
    Config config = pluginConfigFactory.getGlobalPluginConfig(pluginName);
    parseAllGroupsRateLimits(config, groupsCollection);
    rateLimitExceededMsg = parseLimitExceededMsg(config);
  }

  private void parseAllGroupsRateLimits(Config config, GroupsCollection groupsCollection) {
    Map<String, AccountGroup.UUID> groups = getResolvedGroups(config, groupsCollection);
    if (groups.size() == 0) {
      return;
    }
    rateLimits = ArrayTable.create(Arrays.asList(RateLimitType.values()), groups.values());
    for (Entry<String, AccountGroup.UUID> group : groups.entrySet()) {
      parseGroupRateLimits(config, group.getKey(), group.getValue());
    }
  }

  private Map<String, AccountGroup.UUID> getResolvedGroups(
      Config config, GroupsCollection groupsCollection) {
    LinkedHashMap<String, AccountGroup.UUID> groups = new LinkedHashMap<>();
    for (String groupName : config.getSubsections(GROUP_SECTION)) {
      GroupDescription.Basic groupDesc = groupsCollection.parseId(groupName);

      // Group either is mis-configured, never existed, or was deleted/removed since.
      if (groupDesc == null) {
        log.warn("Invalid configuration, group not found: {}", groupName);
      } else {
        groups.put(groupName, groupDesc.getGroupUUID());
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
  Map<AccountGroup.UUID, RateLimit> getRatelimits(RateLimitType rateLimitType) {
    return rateLimits != null ? rateLimits.row(rateLimitType) : ImmutableMap.of();
  }
}
