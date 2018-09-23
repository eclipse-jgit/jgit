load(
    "@com_googlesource_gerrit_bazlets//tools:junit.bzl",
    "junit_tests",
)

junit_tests(
    name = "sshd_apache",
    srcs = glob(["tst/**/*.java"]),
    tags = ["sshd"],
    deps = [
        "//org.eclipse.jgit.test:sshd-helpers",
        "//lib:junit",
        "//org.eclipse.jgit:jgit",
        "//lib:sshd-core",
        "//lib:sshd-sftp",
        "//org.eclipse.jgit.ssh.apache:ssh-apache",
    ],
)
