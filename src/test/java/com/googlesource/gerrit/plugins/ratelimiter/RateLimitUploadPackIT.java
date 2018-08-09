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

import com.google.gerrit.acceptance.GlobalPluginConfig;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Project;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.Test;

@TestPlugin(
    name = "rate-limiter",
    sysModule = "com.googlesource.gerrit.plugins.ratelimiter.Module",
    sshModule = "com.googlesource.gerrit.plugins.ratelimiter.SshModule")
public class RateLimitUploadPackIT extends LightweightPluginDaemonTest {

  @Override
  public void setUp() throws Exception {
    // Create the group before the plugin is loaded since limits per group are
    // resolved at plugin load time.
    addUserToNewGroup("user", "limitGroup");
    super.setUp();
  }

  @Test
  @UseLocalDisk
  @GlobalPluginConfig(
      pluginName = "rate-limiter",
      name = "group.limitGroup.uploadpackperhour",
      value = "1")
  @GlobalPluginConfig(
      pluginName = "rate-limiter",
      name = "configuration.uploadpackLimitExceededMsg",
      value = "Custom message: Limit exceeded ${rateLimit} requests/hour")
  public void requestIsBlockedForGroupAfterRateLimitReached() throws Exception {
    String projectA = "projectA";
    String projectB = "projectB";
    createProjectWithChange(projectA);
    createProjectWithChange(projectB);

    cloneProject(new Project.NameKey(projectA), user);
    exception.expect(TransportException.class);
    cloneProject(new Project.NameKey(projectB), user);
  }

  void addUserToNewGroup(String user, String groupName) throws RestApiException {
    GroupInput in = new GroupInput();
    in.name = groupName;
    in.ownerId = "Administrators";
    gApi.groups().create(in);
    gApi.groups().id(groupName).addMembers(user);
  }

  void createProjectWithChange(String projectName) throws RestApiException {
    ProjectInput input = new ProjectInput();
    input.name = projectName;
    input.createEmptyCommit = true;
    gApi.projects().create(input);
  }
}
