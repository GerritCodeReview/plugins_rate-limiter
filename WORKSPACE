workspace(name = "rate_limiter")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "8dc0767541f16b35d2136eccebffd9ebe2b81133",
    #local_path = "/home/<user>/projects/bazlets",
)

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

gerrit_api(version = "3.3.0-SNAPSHOT")
