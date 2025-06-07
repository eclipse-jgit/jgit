load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "junit",
    testonly = 1,
    srcs = glob(["src/**/*.java"]),
    resource_strip_prefix = "org.eclipse.jgit.junit/resources",
    resources = glob(["resources/**"], allow_empty=True),
    deps = [
        "//lib:junit",
        # We want these deps to be provided_deps
        "//org.eclipse.jgit:jgit",
    ],
)
