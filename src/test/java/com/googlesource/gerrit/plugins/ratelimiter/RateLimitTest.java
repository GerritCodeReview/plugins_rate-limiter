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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class RateLimitTest {

  @Test
  public void testRateLimit() {
    RateLimit rateLimit = new RateLimit(RateLimitType.UPLOAD_PACK_PER_HOUR, 123);

    assertThat(rateLimit.getType()).isEqualTo(RateLimitType.UPLOAD_PACK_PER_HOUR);
    assertThat(rateLimit.getRatePerHour()).isEqualTo(123);
  }
}
