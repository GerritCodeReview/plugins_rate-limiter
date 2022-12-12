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

import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RateLimiterListener implements GitReferenceUpdatedListener {
  private static final Logger log = LoggerFactory.getLogger(RateLimitUploadPack.class);
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final ProjectConfig.Factory projectConfigFactory;
  private final RateLimitUploadPack rateLimitUploadPack;

  @Inject
  public RateLimiterListener(
      MetaDataUpdate.Server metaDataUpdateFactory,
      ProjectConfig.Factory projectConfigFactory,
      RateLimitUploadPack rateLimitUploadPack) {
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.projectConfigFactory = projectConfigFactory;
    this.rateLimitUploadPack = rateLimitUploadPack;
  }

  @Override
  public void onGitReferenceUpdated(Event event) {
    if (event.getRefName().equals(RefNames.REFS_CONFIG)) {
      Project.NameKey p = Project.nameKey(event.getProjectName());
      try {
        ProjectConfig newCfg = parseConfig(p, event.getNewObjectId());
        ProjectConfig oldCfg = parseConfig(p, event.getOldObjectId());
        rateLimitUploadPack.refresh(newCfg, oldCfg);
      } catch (IOException | ConfigInvalidException eIo) {
        log.warn("Failed to parse configuration");
      }
    }
  }

  private ProjectConfig parseConfig(Project.NameKey p, String idStr)
      throws IOException, ConfigInvalidException {
    ObjectId id = ObjectId.fromString(idStr);
    if (ObjectId.zeroId().equals(id)) {
      return null;
    }
    return projectConfigFactory.read(metaDataUpdateFactory.create(p), id);
  }
}
