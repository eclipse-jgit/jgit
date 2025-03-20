load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

SRCS = glob(["src/**/*.java"])

RESOURCES = glob(["resources/**"])

java_library(
    name = "ssh-apache",
    srcs = SRCS,
    resource_strip_prefix = "org.eclipse.jgit.ssh.apache/resources",
    resources = RESOURCES,
    deps = [
        "//lib:bcpg",
        "//lib:bcpkix",
        "//lib:bcprov",
        "//lib:bcutil",
        "//lib:slf4j-api",
        "//lib:sshd-osgi",
        "//lib:sshd-sftp",
        "//org.eclipse.jgit:jgit",
    ],
)
