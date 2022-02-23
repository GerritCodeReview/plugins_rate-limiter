package com.googlesource.gerrit.plugins.ratelimiter;

import static com.googlesource.gerrit.plugins.ratelimiter.ListCommand.FORMAT;
import static com.googlesource.gerrit.plugins.ratelimiter.Module.UPLOAD_PACK_PER_HOUR;

import com.google.common.cache.LoadingCache;
import com.google.gson.*;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class RateLimiterProcessing {

  private final LoadingCache<String, RateLimiter> uploadPackPerHour;
  private final UserResolver userResolver;

  @Inject
  public RateLimiterProcessing(
      @Named(UPLOAD_PACK_PER_HOUR) LoadingCache<String, RateLimiter> uploadPackPerHour,
      UserResolver userResolver) {
    this.uploadPackPerHour = uploadPackPerHour;
    this.userResolver = userResolver;
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

  private void replenishIfPresent(String key) {
    RateLimiter limiter = uploadPackPerHour.getIfPresent(key);
    if (limiter != null) {
      limiter.replenishPermits();
    }
  }
}
