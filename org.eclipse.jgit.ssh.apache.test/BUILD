load(
    "@com_googlesource_gerrit_bazlets//tools:junit.bzl",
    "junit_tests",
)

RESOURCES = glob(["tst-rsrc/**"])

junit_tests(
    name = "sshd_apache",
    srcs = glob(["tst/**/*.java"]),
    resource_strip_prefix = "org.eclipse.jgit.ssh.apache.test/tst-rsrc",
    resources = RESOURCES,
    tags = ["sshd"],
    deps = [
        "//lib:eddsa",
        "//lib:junit",
        "//lib:sshd-osgi",
        "//lib:sshd-putty",
        "//lib:sshd-sftp",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.ssh.apache:ssh-apache",
        "//org.eclipse.jgit.test:sshd-helpers",
    ],
)
