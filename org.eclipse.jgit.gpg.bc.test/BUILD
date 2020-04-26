load(
    "@com_googlesource_gerrit_bazlets//tools:junit.bzl",
    "junit_tests",
)

junit_tests(
    name = "bc",
    srcs = glob(["tst/**/*.java"]),
    tags = ["bc"],
    deps = [
        "//lib:junit",
        "//org.eclipse.jgit.gpg.bc:gpg-bc",
    ],
)
