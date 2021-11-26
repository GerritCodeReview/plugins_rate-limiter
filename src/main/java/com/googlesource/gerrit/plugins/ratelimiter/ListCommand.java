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

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;
import static com.googlesource.gerrit.plugins.ratelimiter.Module.UPLOAD_PACK_PER_HOUR;

import com.google.common.cache.LoadingCache;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.sshd.AdminHighPriorityCommand;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@AdminHighPriorityCommand
@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(
    name = "list",
    description = "Display rate limits statistics",
    runsAt = MASTER_OR_SLAVE)
final class ListCommand extends SshCommand {
  private static final String FORMAT = "%-26s %-17s %-19s %-15s %s";
  private static final String DASHED_LINE =
      "---------------------------------------------------------------------------------------------";

  private final LoadingCache<String, RateLimiter> uploadPackPerHour;
  private final UserResolver userResolver;

  @Inject
  ListCommand(
      @Named(UPLOAD_PACK_PER_HOUR) LoadingCache<String, RateLimiter> uploadPackPerHour,
      UserResolver userResolver) {
    this.uploadPackPerHour = uploadPackPerHour;
    this.userResolver = userResolver;
  }

  @Override
  protected void run() throws UnloggedFailure {
    try {
      stdout.println(DASHED_LINE);
      stdout.println("* " + UPLOAD_PACK_PER_HOUR + " *");
      stdout.println(DASHED_LINE);
      stdout.println(
          String.format(
              FORMAT,
              "Account Id/IP (username)",
              "Permits Per Hour",
              "Available Permits",
              "Used Permits",
              "Replenish in"));
      stdout.println(DASHED_LINE);
      uploadPackPerHour.asMap().entrySet().stream()
          .sorted(Map.Entry.comparingByValue())
          .forEach(this::printEntry);
      stdout.println(DASHED_LINE);
    } catch (Exception e) {
      throw die(e);
    }
  }

  private void printEntry(Entry<String, RateLimiter> entry) {
    stdout.println(
        String.format(
            FORMAT,
            getDisplayValue(entry.getKey()),
            permits(entry.getValue().permitsPerHour()),
            permits(entry.getValue().availablePermits()),
            permits(entry.getValue().usedPermits()),
            Duration.ofSeconds(entry.getValue().remainingTime(TimeUnit.SECONDS))));
  }

  private String permits(int value) {
    return value == Integer.MAX_VALUE ? "unlimited" : Integer.toString(value);
  }

  private String getDisplayValue(String key) {
    Optional<String> currentUser = userResolver.getUserName(key);
    return currentUser.map(name -> key + " (" + name + ")").orElse(key);
  }
}
