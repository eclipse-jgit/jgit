load(
    "@com_googlesource_gerrit_bazlets//tools:junit.bzl",
    "junit_tests",
)

junit_tests(
    name = "sshd_apache",
    srcs = glob(["tst/**/*.java"]),
    tags = ["sshd"],
    deps = [
        "//lib:eddsa",
        "//lib:junit",
        "//lib:sshd-osgi",
        "//lib:sshd-sftp",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit.ssh:junit-ssh",
        "//org.eclipse.jgit.ssh.apache:ssh-apache",
    ],
)
