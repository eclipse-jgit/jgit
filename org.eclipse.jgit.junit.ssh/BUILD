load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "junit-ssh",
    testonly = 1,
    srcs = glob(["src/**/*.java"]),
    resource_strip_prefix = "org.eclipse.jgit.junit.ssh/resources",
    resources = glob(["resources/**"]),
    visibility = [
        "//org.eclipse.jgit.ssh.apache.test:__pkg__",
        "//org.eclipse.jgit.ssh.jsch.test:__pkg__",
    ],
    deps = [
        "//lib:junit",
        "//lib:slf4j-api",
        "//lib:sshd-osgi",
        "//lib:sshd-sftp",
        # We want these deps to be provided_deps
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
    ],
)
