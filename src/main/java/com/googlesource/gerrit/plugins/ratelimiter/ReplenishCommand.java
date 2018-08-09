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

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;
import static com.googlesource.gerrit.plugins.ratelimiter.Module.UPLOAD_PACK_PER_HOUR;

import com.google.common.cache.LoadingCache;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.CapabilityScope;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Option;

@RequiresCapability(value = GlobalCapability.ADMINISTRATE_SERVER, scope = CapabilityScope.CORE)
@CommandMetaData(
    runsAt = MASTER_OR_SLAVE,
    name = "replenish",
    description = "Replenishes uploadpack permits for a given user or remote host.")
final class ReplenishCommand extends SshCommand {

  @Option(name = "--all", usage = "replenish all permits ")
  private boolean all;

  @Option(
      name = "--user",
      metaVar = "USER",
      usage = "full name, email-address, ssh username or account id")
  private List<Account.Id> accountIds = new ArrayList<>();

  @Option(name = "--remotehost", usage = "IP of the remotehost", metaVar = "IP")
  private List<String> remoteHosts = new ArrayList<>();

  private final LoadingCache<String, RateLimiter> uploadPackPerHour;

  @Inject
  ReplenishCommand(
      @Named(UPLOAD_PACK_PER_HOUR) LoadingCache<String, RateLimiter> uploadPackPerHour) {
    this.uploadPackPerHour = uploadPackPerHour;
  }

  @Override
  protected void run() throws UnloggedFailure {
    if (all && (!accountIds.isEmpty() || !remoteHosts.isEmpty())) {
      throw die("cannot use --all with --user or --remotehost");
    }
    if (all) {
      for (RateLimiter rateLimiter : uploadPackPerHour.asMap().values()) {
        rateLimiter.replenishPermits();
      }
      return;
    }
    for (Account.Id accountId : accountIds) {
      replenishIfPresent(Integer.toString(accountId.get()));
    }
    for (String remoteHost : remoteHosts) {
      replenishIfPresent(remoteHost);
    }
  }

  private void replenishIfPresent(String key) {
    RateLimiter limiter = uploadPackPerHour.getIfPresent(key);
    if (limiter != null) {
      limiter.replenishPermits();
    }
  }
}
