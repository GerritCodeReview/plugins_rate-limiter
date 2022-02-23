package com.googlesource.gerrit.plugins.ratelimiter;

import static com.googlesource.gerrit.plugins.ratelimiter.ListCommand.FORMAT;
import static com.googlesource.gerrit.plugins.ratelimiter.Module.UPLOAD_PACK_PER_HOUR;

import com.google.common.cache.LoadingCache;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gson.*;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.errors.ConfigInvalidException;

public class RateLimiterProcessing {

  private final LoadingCache<String, RateLimiter> uploadPackPerHour;
  private final UserResolver userResolver;
  private final AccountResolver accountResolver;

  @Inject
  public RateLimiterProcessing(
      @Named(UPLOAD_PACK_PER_HOUR) LoadingCache<String, RateLimiter> uploadPackPerHour,
      UserResolver userResolver,
      AccountResolver accountResolver) {
    this.uploadPackPerHour = uploadPackPerHour;
    this.userResolver = userResolver;
    this.accountResolver = accountResolver;
  }

  public String listPermits() {
    return uploadPackPerHour.asMap().entrySet().stream()
        .sorted(Map.Entry.comparingByValue())
        .map(
            entry ->
                String.format(
                    FORMAT,
                    getDisplayValue(entry.getKey(), userResolver),
                    permits(entry.getValue().permitsPerHour()),
                    permits(entry.getValue().availablePermits()),
                    permits(entry.getValue().usedPermits()),
                    Duration.ofSeconds(entry.getValue().remainingTime(TimeUnit.SECONDS))))
        .reduce("", String::concat);
  }

  public String listPermitsAsJson() {
    ArrayList<String> permitList = new ArrayList<>();
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    uploadPackPerHour.asMap().entrySet().stream()
        .sorted(Map.Entry.comparingByValue())
        .forEach(
            entry -> {
              JsonObject jsonObject = new JsonObject();
              jsonObject.addProperty("AccountId", getDisplayValue(entry.getKey(), userResolver));
              jsonObject.addProperty(
                  "permits_per_hour", permits(entry.getValue().permitsPerHour()));
              jsonObject.addProperty(
                  "available_permits", permits(entry.getValue().availablePermits()));
              jsonObject.addProperty("used_permit", permits(entry.getValue().usedPermits()));
              jsonObject.addProperty(
                  "replenish_in",
                  Duration.ofSeconds(entry.getValue().remainingTime(TimeUnit.SECONDS)).toString());
              permitList.add(jsonObject.toString());
            });
    JsonElement je = JsonParser.parseString(permitList.toString());
    return gson.toJson(je);
  }

  private String permits(int value) {
    return value == Integer.MAX_VALUE ? "unlimited" : Integer.toString(value);
  }

  private String getDisplayValue(String key, UserResolver userResolver) {
    Optional<String> currentUser = userResolver.getUserName(key);
    return currentUser.map(name -> key + " (" + name + ")").orElse(key);
  }

  public void replenish(boolean all, List<Account.Id> accountIds, List<String> remoteHosts) {
    if (all && (!accountIds.isEmpty() || !remoteHosts.isEmpty())) {
      throw new IllegalArgumentException("cannot use --all with --user or --remotehost");
    }
    if (all) {
      for (RateLimiter rateLimiter : uploadPackPerHour.asMap().values()) {
        rateLimiter.replenishPermits();
      }
      return;
    }
    for (Account.Id accountId : accountIds) {
      replenishIfPresent(Integer.toString(accountId.get()));
    }
    for (String remoteHost : remoteHosts) {
      replenishIfPresent(remoteHost);
    }
    return;
  }

  List<Account.Id> convertToAccountId(String[] usernames)
      throws ConfigInvalidException, IOException, ResourceNotFoundException {
    ArrayList<Account.Id> accountIds = new ArrayList<>();
    for (String user : usernames) {
      AccountResolver.Result accountId = accountResolver.resolve(user);
      if (accountId.asIdSet().isEmpty())
        throw new ResourceNotFoundException(String.format("User %s not found", user));
      accountIds.addAll(accountId.asIdSet());
    }
    return accountIds;
  }

  private void replenishIfPresent(String key) {
    RateLimiter limiter = uploadPackPerHour.getIfPresent(key);
    if (limiter != null) {
      limiter.replenishPermits();
    }
  }
}
