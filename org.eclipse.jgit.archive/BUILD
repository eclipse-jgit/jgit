load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "jgit-archive",
    srcs = glob(
        ["src/**/*.java"],
        exclude = ["src/org/eclipse/jgit/archive/FormatActivator.java"],
    ),
    resource_strip_prefix = "org.eclipse.jgit.archive/resources",
    resources = glob(["resources/**"]),
    deps = [
        "//lib:commons-compress",
        "//lib:commons-io",
        "//lib:commons-lang3",
        # We want these deps to be provided_deps
        "//org.eclipse.jgit:jgit",
    ],
)
