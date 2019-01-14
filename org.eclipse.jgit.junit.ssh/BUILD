package(default_visibility = ["//visibility:public"])

java_library(
    name = "junit-ssh",
    testonly = 1,
    srcs = glob(["src/**/*.java"]),
    resource_strip_prefix = "org.eclipse.jgit.junit.ssh/resources",
    resources = glob(["resources/**"]),
    deps = [
        "//lib:sshd-osgi",
        "//lib:sshd-sftp",
        # We want these deps to be provided_deps
        "//org.eclipse.jgit:jgit",
    ],
)
