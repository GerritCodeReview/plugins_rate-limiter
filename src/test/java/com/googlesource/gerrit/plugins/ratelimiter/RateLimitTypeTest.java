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

public class RateLimitTypeTest {

  @Test
  public void testToString() {
    assertThat(RateLimitType.UPLOAD_PACK_PER_HOUR.toString()).isEqualTo("uploadpackperhour");
  }

  @Test
  public void testFromString() {
    assertThat(RateLimitType.from("uploadpackperhour"))
        .isEqualTo(RateLimitType.UPLOAD_PACK_PER_HOUR);
    assertThat(RateLimitType.from("non-existing-type")).isNull();
  }
}
