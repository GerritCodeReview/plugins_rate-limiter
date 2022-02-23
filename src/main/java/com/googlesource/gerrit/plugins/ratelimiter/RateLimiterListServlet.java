package com.googlesource.gerrit.plugins.ratelimiter;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.CapabilityScope;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RequiresCapability(value = GlobalCapability.ADMINISTRATE_SERVER, scope = CapabilityScope.CORE)
@Singleton
public class RateLimiterListServlet extends HttpServlet {
  private final RateLimiterProcessing rateLimiterProcessing;
  private final PermissionBackend permissionBackend;

  @Inject
  RateLimiterListServlet(
      RateLimiterProcessing rateLimiterProcessing, PermissionBackend permissionBackend) {
    this.rateLimiterProcessing = rateLimiterProcessing;
    this.permissionBackend = permissionBackend;
  }

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

  protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    } catch (AuthException | PermissionBackendException e) {
      setResponse(res, HttpServletResponse.SC_FORBIDDEN, "Authentication failed");
      return;
    }
    if ("/replenish".equals(req.getPathInfo())) {

      try {
        Optional<RateLimiterRequest> request = getRequest(req);
        setResponse(res, HttpServletResponse.SC_OK, request.toString());
      } catch (IOException e) {
        setResponse(res, HttpServletResponse.SC_FORBIDDEN, "Failed to parse request");
      }
    }
  }

  private Optional<RateLimiterRequest> getRequest(HttpServletRequest req) throws IOException {
    Gson gson = new Gson();
    RateLimiterRequest rateLimiterRequest;
    try {
      BufferedReader reader = req.getReader();
      rateLimiterRequest = gson.fromJson(reader, RateLimiterRequest.class);
    } catch (JsonParseException e) {
      return Optional.empty();
    }
    return Optional.of(rateLimiterRequest);
  }

  private void setResponse(HttpServletResponse httpResponse, int statusCode, String value)
      throws IOException {
    httpResponse.setContentType("text/plain");
    httpResponse.setStatus(statusCode);
    PrintWriter writer = httpResponse.getWriter();
    writer.print(value);
  }

  private class RateLimiterRequest {
    private String user;
    private String remotehost;
    private boolean all;

    public String getUser() {
      return user;
    }

    public void setUser(String user) {
      this.user = user;
    }

    public String getRemotehost() {
      return remotehost;
    }

    public void setRemotehost(String remotehost) {
      this.remotehost = remotehost;
    }

    public boolean isAll() {
      return all;
    }

    public void setAll(boolean all) {
      this.all = all;
    }

    @Override
    public String toString() {
      return "RateLimiterRequest{"
          + "user='"
          + user
          + '\''
          + ", remotehost='"
          + remotehost
          + '\''
          + ", all="
          + all
          + '}';
    }
  }
}
