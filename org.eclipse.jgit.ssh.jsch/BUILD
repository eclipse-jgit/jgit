load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

SRCS = glob(["src/**/*.java"])

RESOURCES = glob(["resources/**"])

java_library(
    name = "ssh-jsch",
    srcs = SRCS,
    resource_strip_prefix = "org.eclipse.jgit.ssh.jsch/resources",
    resources = RESOURCES,
    deps = [
        "//lib:jsch",
        "//lib:jzlib",
        "//lib:slf4j-api",
        "//org.eclipse.jgit:jgit",
    ],
)
