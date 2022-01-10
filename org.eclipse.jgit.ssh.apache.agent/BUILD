load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

SRCS = glob(["src/**/*.java"])

RESOURCES = glob(["resources/**"])

java_library(
    name = "ssh-apache-agent",
    srcs = SRCS,
    resource_strip_prefix = "org.eclipse.jgit.ssh.apache.agent/resources",
    resources = RESOURCES,
    deps = [
        "//lib:jna",
        "//lib:jna-platform",
        "//lib:slf4j-api",
        "//lib:sshd-osgi",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.ssh.apache:ssh-apache",
    ],
)
