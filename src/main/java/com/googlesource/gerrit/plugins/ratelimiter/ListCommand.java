import java.util.Optional;
import java.util.concurrent.TimeUnit;
@AdminHighPriorityCommand
@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(
    name = "list",
    description = "Display rate limits statistics",
    runsAt = MASTER_OR_SLAVE)
final class ListCommand extends SshCommand {
  private static final String FORMAT = "%-26s %-17s %-19s %-15s %s";
  private static final String DASHED_LINE =
      "---------------------------------------------------------------------------------------------";
  private final LoadingCache<String, RateLimiter> uploadPackPerHour;
  private final UserResolver userResolver;
  @Inject
  ListCommand(
      @Named(UPLOAD_PACK_PER_HOUR) LoadingCache<String, RateLimiter> uploadPackPerHour,
      UserResolver userResolver) {
    this.uploadPackPerHour = uploadPackPerHour;
    this.userResolver = userResolver;
    try {
      stdout.println(DASHED_LINE);
      stdout.println("* " + UPLOAD_PACK_PER_HOUR + " *");
      stdout.println(DASHED_LINE);
      stdout.println(
          String.format(
              FORMAT,
              "Account Id/IP (username)",
              "Permits Per Hour",
              "Available Permits",
              "Used Permits",
              "Replenish in"));
      stdout.println(DASHED_LINE);
      uploadPackPerHour.asMap().entrySet().stream()
          .sorted(Map.Entry.comparingByValue())
          .forEach(this::printEntry);
      stdout.println(DASHED_LINE);
    } catch (Exception e) {
      throw die(e);
    }
  }
  private void printEntry(Entry<String, RateLimiter> entry) {
    stdout.println(
        String.format(
            FORMAT,
            getDisplayValue(entry.getKey()),
            permits(entry.getValue().permitsPerHour()),
            permits(entry.getValue().availablePermits()),
            permits(entry.getValue().usedPermits()),
            Duration.ofSeconds(entry.getValue().remainingTime(TimeUnit.SECONDS))));
  }
  private String permits(int value) {
    return value == Integer.MAX_VALUE ? "unlimited" : Integer.toString(value);
  }
  private String getDisplayValue(String key) {
    Optional<String> currentUser = userResolver.getUserName(key);
    return currentUser.map(name -> key + " (" + name + ")").orElse(key);
  }
}
