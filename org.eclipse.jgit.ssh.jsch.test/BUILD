load(
    "@com_googlesource_gerrit_bazlets//tools:junit.bzl",
    "junit_tests",
)

junit_tests(
    name = "jsch",
    srcs = glob(["tst/**/*.java"]),
    tags = ["jsch"],
    deps = [
        "//lib:eddsa",
        "//lib:junit",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.ssh.jsch:ssh-jsch",
        "//org.eclipse.jgit.test:sshd-helpers",
    ],
)
