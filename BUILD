load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:junit.bzl", "junit_tests")
load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
    "gerrit_plugin",
)

gerrit_plugin(
    name = "rate-limiter",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: rate-limiter",
        "Gerrit-Module: com.googlesource.gerrit.plugins.ratelimiter.Module",
        "Gerrit-SshModule: com.googlesource.gerrit.plugins.ratelimiter.SshModule",
        "Gerrit-HttpModule: com.googlesource.gerrit.plugins.ratelimiter.HttpModule",
    ],
    resources = glob(["src/main/resources/**/*"]),
)

java_library(
    name = "rate-limiter__plugin_test_deps",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":rate-limiter__plugin",
    ],
)

junit_tests(
    name = "rate-limiter_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = [
        "rate-limiter",
    ],
    deps = [
        ":rate-limiter__plugin_test_deps",
    ],
)
