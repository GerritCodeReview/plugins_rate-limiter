// Copyright (C) 2023 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.ratelimiter.scenarios

import com.google.gerrit.scenarios.{CreateProject, DeleteProject, GitSimulation}
import io.gatling.core.Predef._
import io.gatling.core.feeder.FeederBuilder
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._

import scala.concurrent.duration._

class ConfigRateLimiterInAllproject extends GitSimulation {
  private val data: FeederBuilder = jsonFile(resource).convert(keys).circular

  val test: ScenarioBuilder = scenario(uniqueName)
      .feed(data)
      .exec(http(uniqueName).get("${url}")
          .check(regex(
            "(\\(admin\\)\",\\n)" +
                "([\\s]+\"permits_per_hour\":\\s\"10\",\\n)" +
                "([\\s]+\"available_permits\":\\s\"9\",\\n)" +
                "([\\s]+\"used_permit\":\\s\"1\",\\n)")
          ))
  private val createProject = new CreateProject(projectName)
  private val deleteProject = new DeleteProject(projectName)
  private val cloneWithAuthorization = new CloneWithAuthorization(projectName)
  private val replenishUserRateLimit = new ReplenishUserRateLimit

  setUp(
    createProject.test.inject(
      nothingFor(stepWaitTime(createProject) seconds),
      atOnceUsers(single)
    ),
    replenishUserRateLimit.test.inject(
      nothingFor(stepWaitTime(this) seconds),
      atOnceUsers(single)
    ),
    cloneWithAuthorization.test.inject(
      nothingFor(stepWaitTime(cloneWithAuthorization) seconds),
      atOnceUsers(single)
    ).protocols(gitProtocol),
    test.inject(
      nothingFor(stepWaitTime(this) seconds),
      atOnceUsers(single)
    ),
    deleteProject.test.inject(
      nothingFor(stepWaitTime(deleteProject) seconds),
      atOnceUsers(single)
    ),
  ).protocols(httpProtocol)
}
