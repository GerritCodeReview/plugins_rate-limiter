// Copyright (C) 2022 The Android Open Source Project
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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.annotations.CapabilityScope;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.errors.ConfigInvalidException;

@RequiresCapability(value = GlobalCapability.ADMINISTRATE_SERVER, scope = CapabilityScope.CORE)
@Singleton
public class RateLimiterServlet extends HttpServlet {
  private final RateLimiterProcessing rateLimiterProcessing;
  private final PermissionBackend permissionBackend;

  @Inject
  RateLimiterServlet(
      RateLimiterProcessing rateLimiterProcessing, PermissionBackend permissionBackend) {
    this.rateLimiterProcessing = rateLimiterProcessing;
    this.permissionBackend = permissionBackend;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    } catch (AuthException | PermissionBackendException e) {
      setResponse(res, HttpServletResponse.SC_FORBIDDEN, "Authentication failed");
      return;
    }
    if ("/list".equals(req.getPathInfo())) {
      setResponse(res, HttpServletResponse.SC_OK, rateLimiterProcessing.listPermitsAsJson());
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    } catch (AuthException | PermissionBackendException e) {
      setResponse(res, HttpServletResponse.SC_FORBIDDEN, "Authentication failed");
      return;
    }
    if ("/replenish".equals(req.getPathInfo())) {
      try {
        String[] replenishForUsers = new String[0];
        List<String> replenishForHosts = Collections.EMPTY_LIST;
        boolean replenishForAll = false;
        if (req.getParameter("user") != null) {
          replenishForUsers = req.getParameterValues("user");
        }
        if (req.getParameter("remotehost") != null) {
          replenishForHosts = Arrays.asList(req.getParameterValues("host"));
        }
        if (req.getParameter("all") != null) {
          replenishForAll = "true".equals(req.getParameter("all"));
        }

        List<Account.Id> accountIds = rateLimiterProcessing.convertToAccountId(replenishForUsers);
        rateLimiterProcessing.replenish(replenishForAll, accountIds, replenishForHosts);
        setResponse(res, HttpServletResponse.SC_NO_CONTENT, accountIds.toString());
      } catch (ResourceNotFoundException | ConfigInvalidException | IllegalArgumentException e) {
        setResponse(res, HttpServletResponse.SC_FORBIDDEN, "Fatal: " + e.getMessage());
      }
    }
  }

  private void setResponse(HttpServletResponse httpResponse, int statusCode, String value)
      throws IOException {
    httpResponse.setContentType("application/json");
    httpResponse.setStatus(statusCode);
    PrintWriter writer = httpResponse.getWriter();
    writer.print(value);
  }
}
