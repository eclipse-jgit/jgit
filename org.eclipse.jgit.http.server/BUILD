load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

filegroup(
    name = "srcs",
    srcs = glob(["src/**/*.java"]),
    visibility = [
        "//org.eclipse.jgit.http.server.ee8:__pkg__",
        "//tools/jgit-ee8:__pkg__",
    ],
)

filegroup(
    name = "jgit-servlet-resources",
    srcs = glob(["resources/**"]),
)

java_library(
    name = "jgit-servlet",
    srcs = [":srcs"],
    resource_strip_prefix = "org.eclipse.jgit.http.server/resources",
    resources = [":jgit-servlet-resources"],
    deps = [
        "//lib:servlet-api",
        # We want these deps to be provided_deps
        "//org.eclipse.jgit:jgit",
    ],
)
