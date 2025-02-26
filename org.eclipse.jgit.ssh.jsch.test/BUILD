load(
    "@com_googlesource_gerrit_bazlets//tools:junit.bzl",
    "junit_tests",
)

junit_tests(
    name = "jsch",
    srcs = glob(["tst/**/*.java"]),
    tags = ["jsch"],
    deps = [
        "//lib:bcpkix",
        "//lib:bcprov",
        "//lib:bcutil",
        "//lib:jsch",
        "//lib:junit",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
        "//org.eclipse.jgit.junit.ssh:junit-ssh",
        "//org.eclipse.jgit.ssh.jsch:ssh-jsch",
    ],
)
