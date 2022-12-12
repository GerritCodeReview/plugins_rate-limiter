// Copyright (C) 2017 The Android Open Source Project
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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.CachedProjectConfig;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.ProvisionException;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConfigurationTest {
  private static final String PLUGIN_NAME = "rate-limiter";

  @Mock private PluginConfigFactory pluginConfigFactoryMock;
  @Mock private GroupResolver groupsCollectionMock;
  @Mock private GroupDescription.Basic administratorsGroupDescMock;
  @Mock private GroupDescription.Basic someGroupDescMock;
  @Mock private ProjectConfig newProjectConfig;
  @Mock private ProjectConfig oldProjectConfig;
  @Mock private CachedProjectConfig cachedProjectConfig;
  private Config globalPluginConfig;
  ImmutableMap<String, String> cacheableConfig;
  private final int validRate = 123;
  private final int validWarningRate = 50;
  private final int newValidRate = 100;
  private final int newValidWarningRate = 60;
  private final int validTimeLapse = 10;
  private final int newValidTimeLapse = 20;
  private final String groupTagName = "group";
  private final String rateLimiterConfigInAllProject =
      String.format(
          "[%s \"someGroup\"]\n "
              + "uploadpackperhour = %s\n "
              + "uploadpackperhourwarn = %s\n "
              + "timelapseinminutes = %s",
          groupTagName, newValidRate, newValidWarningRate, newValidTimeLapse);
  private final String badConfiguration =
      String.format(
          "[%s \"someGroup\"\n "
              + "uploadpackperhour = %s\n "
              + "uploadpackperhourwarn = %s\n "
              + "timelapseinminutes = %s",
          groupTagName, newValidRate, newValidWarningRate, newValidTimeLapse);

  @Before
  public void setUp() {
    globalPluginConfig = new Config();

    when(pluginConfigFactoryMock.getGlobalPluginConfig(PLUGIN_NAME)).thenReturn(globalPluginConfig);

    when(administratorsGroupDescMock.getGroupUUID()).thenReturn(AccountGroup.uuid("admin_uuid"));
    when(groupsCollectionMock.parseId("Administrators")).thenReturn(administratorsGroupDescMock);

    when(someGroupDescMock.getName()).thenReturn("someGroup");
    when(someGroupDescMock.getGroupUUID()).thenReturn(AccountGroup.uuid("some_uuid"));
    when(groupsCollectionMock.parseId("someGroup")).thenReturn(someGroupDescMock);

    when(newProjectConfig.getCacheable()).thenReturn(cachedProjectConfig);
  }

  private Configuration getConfiguration() {
    return new Configuration(pluginConfigFactoryMock, PLUGIN_NAME, groupsCollectionMock);
  }

  @Test
  public void testEmptyConfig() {
    assertThat(getConfiguration().getRateLimits(RateLimitType.UPLOAD_PACK_PER_HOUR)).isEmpty();
  }

  @Test
  public void testConfigtFromAllProjectConfig() {
    // Config in table
    globalPluginConfig.setInt(
        groupTagName,
        someGroupDescMock.getName(),
        RateLimitType.UPLOAD_PACK_PER_HOUR.toString(),
        validRate);
    globalPluginConfig.setInt(
        groupTagName,
        someGroupDescMock.getName(),
        RateLimitType.UPLOAD_PACK_PER_HOUR_WARN.toString(),
        validWarningRate);
    globalPluginConfig.setInt(
        groupTagName,
        someGroupDescMock.getName(),
        RateLimitType.TIME_LAPSE_IN_MINUTES.toString(),
        validTimeLapse);

    cacheableConfig =
        ImmutableMap.<String, String>builder()
            .put("rate-limiter.config", rateLimiterConfigInAllProject)
            .build();
    when(cachedProjectConfig.getProjectLevelConfigs()).thenReturn(cacheableConfig);

    Configuration configuration = getConfiguration();

    validateConfiginTable(configuration, validRate, validWarningRate, validTimeLapse);
    configuration.refreshTable(
        newProjectConfig,
        oldProjectConfig); // Change config in table base in config in all-projects
    validateConfiginTable(configuration, newValidRate, newValidWarningRate, newValidTimeLapse);
  }

  @Test
  public void testInvalidConfiginAllProjects() {
    globalPluginConfig.setInt(
        groupTagName,
        someGroupDescMock.getName(),
        RateLimitType.UPLOAD_PACK_PER_HOUR.toString(),
        validRate);
    globalPluginConfig.setInt(
        groupTagName,
        someGroupDescMock.getName(),
        RateLimitType.UPLOAD_PACK_PER_HOUR_WARN.toString(),
        validWarningRate);
    globalPluginConfig.setInt(
        groupTagName,
        someGroupDescMock.getName(),
        RateLimitType.TIME_LAPSE_IN_MINUTES.toString(),
        validTimeLapse);

    cacheableConfig =
        ImmutableMap.<String, String>builder().put("rate-limiter.config", badConfiguration).build();
    when(cachedProjectConfig.getProjectLevelConfigs()).thenReturn(cacheableConfig);

    Configuration configuration = getConfiguration();

    validateConfiginTable(configuration, validRate, validWarningRate, validTimeLapse);
    configuration.refreshTable(newProjectConfig, oldProjectConfig);
    validateConfiginTable(configuration, validRate, validWarningRate, validTimeLapse);
  }

  @Test
  public void testNoConfiginAllProjects() {
    globalPluginConfig.setInt(
        groupTagName,
        someGroupDescMock.getName(),
        RateLimitType.UPLOAD_PACK_PER_HOUR.toString(),
        validRate);
    globalPluginConfig.setInt(
        groupTagName,
        someGroupDescMock.getName(),
        RateLimitType.UPLOAD_PACK_PER_HOUR_WARN.toString(),
        validWarningRate);
    globalPluginConfig.setInt(
        groupTagName,
        someGroupDescMock.getName(),
        RateLimitType.TIME_LAPSE_IN_MINUTES.toString(),
        validTimeLapse);

    cacheableConfig = ImmutableMap.<String, String>builder().build();
    ;
    when(cachedProjectConfig.getProjectLevelConfigs()).thenReturn(cacheableConfig);

    Configuration configuration = getConfiguration();

    validateConfiginTable(configuration, validRate, validWarningRate, validTimeLapse);
    configuration.refreshTable(newProjectConfig, oldProjectConfig);
    validateConfiginTable(configuration, validRate, validWarningRate, validTimeLapse);
  }

  @Test
  public void testUploadPackPerHourRateLimit() {
    globalPluginConfig.setInt(
        groupTagName,
        someGroupDescMock.getName(),
        RateLimitType.UPLOAD_PACK_PER_HOUR.toString(),
        validRate);

    Map<AccountGroup.UUID, RateLimit> rateLimit =
        getConfiguration().getRateLimits(RateLimitType.UPLOAD_PACK_PER_HOUR);
    assertThat(rateLimit).hasSize(1);
    assertThat(rateLimit.get(someGroupDescMock.getGroupUUID()).getRatePerHour())
        .isEqualTo(validRate);
  }

  @Test
  public void testInvalidRateLimitType() {
    globalPluginConfig.setInt(
        groupTagName, someGroupDescMock.getName(), "invalidTypePerHour", validRate);

    ProvisionException thrown = assertThrows(ProvisionException.class, () -> getConfiguration());
    assertThat(thrown)
        .hasMessageThat()
        .contains("Invalid configuration, unsupported rate limit type: invalidTypePerHour");
  }

  @Test
  public void testInvalidRateLimitValue() {
    String invalidType = "dummyType";

    globalPluginConfig.setString(
        groupTagName,
        someGroupDescMock.getName(),
        RateLimitType.UPLOAD_PACK_PER_HOUR.toString(),
        invalidType);

    String expectedMessage =
        String.format(
            "Invalid configuration, 'rate limit value '%s' for 'group.someGroup.uploadpackperhour' is not a valid number",
            invalidType);
    ProvisionException thrown = assertThrows(ProvisionException.class, () -> getConfiguration());
    assertThat(thrown).hasMessageThat().contains(expectedMessage);
  }

  @Test
  public void testInvalidGroup() {
    // Set a good group and a bad and ensure the good is still parsed

    globalPluginConfig.setInt(
        groupTagName,
        someGroupDescMock.getName(),
        RateLimitType.UPLOAD_PACK_PER_HOUR.toString(),
        validRate);

    globalPluginConfig.setString(
        groupTagName,
        "nonexistingGroup",
        RateLimitType.UPLOAD_PACK_PER_HOUR.toString(),
        "badGroup");

    Map<AccountGroup.UUID, RateLimit> rateLimit =
        getConfiguration().getRateLimits(RateLimitType.UPLOAD_PACK_PER_HOUR);
    assertThat(rateLimit).hasSize(1);
    assertThat(rateLimit.get(someGroupDescMock.getGroupUUID()).getRatePerHour())
        .isEqualTo(validRate);
  }

  @Test
  public void testNoUploadPackPerHourRateLimitForAGroup() throws ConfigInvalidException {
    globalPluginConfig.fromText("[group \"Administrators\"]");

    Map<AccountGroup.UUID, RateLimit> rateLimit =
        getConfiguration().getRateLimits(RateLimitType.UPLOAD_PACK_PER_HOUR);
    assertThat(rateLimit).hasSize(1);
    assertThat(rateLimit.get(administratorsGroupDescMock.getGroupUUID())).isNull();
  }

  @Test
  public void testDefaultRateLimitExceededMsg() {
    assertThat(getConfiguration().getRateLimitExceededMsg())
        .isEqualTo("Exceeded rate limit of ${rateLimit} fetch requests/hour");
  }

  @Test
  public void testRateLimitExceededMsg() {
    String msg = "Some error message.";
    globalPluginConfig.setString("configuration", null, "uploadpackLimitExceededMsg", msg);
    assertThat(getConfiguration().getRateLimitExceededMsg()).isEqualTo(msg);
  }

  private void validateConfiginTable(
      Configuration configuration, int rateToCheck, int warningToCheck, int timeLapseToCheck) {
    Map<AccountGroup.UUID, RateLimit> rateLimit =
        configuration.getRateLimits(RateLimitType.UPLOAD_PACK_PER_HOUR);
    assertThat(rateLimit).hasSize(1);
    assertThat(rateLimit.get(someGroupDescMock.getGroupUUID()).getRatePerHour())
        .isEqualTo(rateToCheck);
    Map<AccountGroup.UUID, RateLimit> warningRate =
        configuration.getRateLimits(RateLimitType.UPLOAD_PACK_PER_HOUR_WARN);
    assertThat(warningRate).hasSize(1);
    assertThat(warningRate.get(someGroupDescMock.getGroupUUID()).getRatePerHour())
        .isEqualTo(warningToCheck);
    Map<AccountGroup.UUID, RateLimit> timeLapse =
        configuration.getRateLimits(RateLimitType.TIME_LAPSE_IN_MINUTES);
    assertThat(timeLapse).hasSize(1);
    assertThat(timeLapse.get(someGroupDescMock.getGroupUUID()).getRatePerHour())
        .isEqualTo(timeLapseToCheck);
  }
}
