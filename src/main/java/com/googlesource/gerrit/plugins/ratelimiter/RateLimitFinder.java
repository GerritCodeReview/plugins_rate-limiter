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

import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

@Singleton
class RateLimitFinder {

  private final Configuration configuration;
  private final UserResolver userResolver;
  private final AccountGroup.UUID anonymousUsersGroupUUID;

  @Inject
  RateLimitFinder(
      Configuration configuration,
      UserResolver userResolver,
      SystemGroupBackend systemGroupBackend) {
    this.configuration = configuration;
    this.userResolver = userResolver;
    anonymousUsersGroupUUID = systemGroupBackend.get(ANONYMOUS_USERS).getGroupUUID();
  }

  Optional<RateLimit> find(RateLimitType rateLimitType, String key) {
    Optional<IdentifiedUser> currentUser = userResolver.getIdentifiedUser(key);
    return currentUser.isPresent()
        ? firstMatching(rateLimitType, currentUser.get())
        : getRateLimit(rateLimitType, anonymousUsersGroupUUID);
  }

  /**
   * @param rateLimitType type of rate limit
   * @param user identified user
   * @return the rate limit matching the first configured group limit in which the user is a member
   */
  private Optional<RateLimit> firstMatching(RateLimitType rateLimitType, IdentifiedUser user) {
    Map<AccountGroup.UUID, RateLimit> limitsPerGroupUUID =
        configuration.getRatelimits(rateLimitType);
    if (!limitsPerGroupUUID.isEmpty()) {
      GroupMembership memberShip = user.getEffectiveGroups();
      for (Entry<AccountGroup.UUID, RateLimit> limitPerGroupUUID : limitsPerGroupUUID.entrySet()) {
        if (memberShip.contains(limitPerGroupUUID.getKey())) {
          return Optional.ofNullable(limitPerGroupUUID.getValue());
        }
      }
    }
    return Optional.empty();
  }

  /**
   * @param rateLimitType type of rate limit
   * @param groupUUID uuid of group to lookup up rate limit for
   * @return rate limit
   */
  private Optional<RateLimit> getRateLimit(
      RateLimitType rateLimitType, AccountGroup.UUID groupUUID) {
    Map<AccountGroup.UUID, RateLimit> limits = configuration.getRatelimits(rateLimitType);
    return limits.isEmpty() ? Optional.empty() : Optional.ofNullable(limits.get(groupUUID));
  }
}
