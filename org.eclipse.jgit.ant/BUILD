load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "jgit-ant",
    srcs = glob(["src/**/*.java"]),
    resource_strip_prefix = "org.eclipse.jgit.ant/resources",
    resources = glob(["resources/**"]),
    deps = [
        "//lib:ant",
        # We want this dep to be a provided_dep
        "//org.eclipse.jgit:jgit",
    ],
)
