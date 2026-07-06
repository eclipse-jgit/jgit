load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

filegroup(
    name = "srcs",
    srcs = glob(["src/**/*.java"]),
    visibility = [
        "//org.eclipse.jgit.lfs.server.ee8:__pkg__",
        "//tools/jgit-ee8:__pkg__",
    ],
)

filegroup(
    name = "jgit-lfs-server-resources",
    srcs = glob(["resources/**"]),
)

java_library(
    name = "jgit-lfs-server",
    srcs = [":srcs"],
    resource_strip_prefix = "org.eclipse.jgit.lfs.server/resources",
    resources = [":jgit-lfs-server-resources"],
    deps = [
        "//lib:gson",
        "//lib:httpcore",
        "//lib:jakarta-servlet-api",
        "//lib:slf4j-api",
        "//org.eclipse.jgit.http.apache:http-apache",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.lfs:jgit-lfs",
    ],
)
