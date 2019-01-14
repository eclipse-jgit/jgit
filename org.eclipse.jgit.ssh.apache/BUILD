package(default_visibility = ["//visibility:public"])

SRCS = glob(["src/**/*.java"])

RESOURCES = glob(["resources/**"])

java_library(
    name = "ssh-apache",
    srcs = SRCS,
    resource_strip_prefix = "org.eclipse.jgit.ssh.apache/resources",
    resources = RESOURCES,
    deps = [
        "//lib:eddsa",
        "//lib:slf4j-api",
        "//lib:sshd-osgi",
        "//lib:sshd-sftp",
        "//org.eclipse.jgit:jgit",
    ],
)
