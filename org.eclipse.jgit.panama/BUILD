load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

SRCS = glob(["src/**/*.java"])

RESOURCES = glob(["resources/**"])

java_library(
    name = "panama",
    srcs = SRCS,
    resource_strip_prefix = "org.eclipse.jgit.panama/resources",
    resources = RESOURCES,
    deps = [
        "//lib:slf4j-api",
        "//org.eclipse.jgit:jgit",
    ],
)
