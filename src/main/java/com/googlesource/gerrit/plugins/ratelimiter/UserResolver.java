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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.inject.Inject;
import java.util.Optional;
import java.util.regex.Pattern;

class UserResolver {
  private static final Pattern DIGITS = Pattern.compile("[0-9]+");

  private final GenericFactory userFactory;

  @Inject
  UserResolver(IdentifiedUser.GenericFactory userFactory) {
    this.userFactory = userFactory;
  }

  Optional<IdentifiedUser> getIdentifiedUser(String key) {
    return isNumeric(key)
        ? Optional.ofNullable(userFactory.create(new Account.Id(Integer.parseInt(key))))
        : Optional.empty();
  }

  Optional<String> getUserName(String key) {
    Optional<IdentifiedUser> user = getIdentifiedUser(key);
    return user.isPresent()
        ? Optional.ofNullable(user.get().getUserName().get())
        : Optional.empty();
  }

  private static boolean isNumeric(String key) {
    return DIGITS.matcher(key).matches();
  }
}
