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

package com.googlesource.gerrit.plugins.ratelimiter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener.Event;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectConfig;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RateLimiterListenerTest {
  @Mock private ProjectConfig.Factory projectConfigFactoryMock;
  @Mock private MetaDataUpdate.Server metaDataUpdateFactorymock;
  @Mock private RateLimitUploadPack rateLimitUploadPack;
  private AllProjectsName allProjectsName;
  private RateLimiterListener rateLimiterListener;
  private final Boolean isReplica = false;
  private final String someId = "0070000700007000070000700007000070000700";
  private final String ALL_PROJECTS = "All-Projects";
  private final String SOME_PROJECT = "Something";

  @Before
  public void setUp() throws ConfigInvalidException, IOException {
    allProjectsName = new AllProjectsName("All-Projects");
    rateLimiterListener =
        new RateLimiterListener(
            allProjectsName,
            isReplica,
            metaDataUpdateFactorymock,
            projectConfigFactoryMock,
            rateLimitUploadPack);
  }

  private Event configChangeEvent(String projectName) {
    return new Event() {
      @Override
      public String getRefName() {
        return RefNames.REFS_CONFIG;
      }

      @Override
      public String getOldObjectId() {
        return someId;
      }

      @Override
      public String getNewObjectId() {
        return someId;
      }

      @Override
      public boolean isCreate() {
        return false;
      }

      @Override
      public boolean isDelete() {
        return false;
      }

      @Override
      public boolean isNonFastForward() {
        return false;
      }

      @Override
      public AccountInfo getUpdater() {
        return null;
      }

      @Override
      public String getProjectName() {
        return projectName;
      }

      @Override
      public NotifyHandling getNotify() {
        return null;
      }
    };
  }

  @Test
  public void shouldTriggerRefresh() {
    rateLimiterListener.onGitReferenceUpdated(configChangeEvent(ALL_PROJECTS));
    verify(rateLimitUploadPack).refresh(any(), any());
  }

  @Test
  public void shouldNotTriggerRefresh() {
    rateLimiterListener.onGitReferenceUpdated(configChangeEvent(SOME_PROJECT));
    verify(rateLimitUploadPack, never()).refresh(any(), any());
  }
}
