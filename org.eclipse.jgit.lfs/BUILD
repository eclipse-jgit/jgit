load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "jgit-lfs",
    srcs = glob(["src/**/*.java"]),
    resource_strip_prefix = "org.eclipse.jgit.lfs/resources",
    resources = glob(["resources/**"]),
    deps = [
        "//lib:gson",
        "//org.eclipse.jgit:jgit",
    ],
)
