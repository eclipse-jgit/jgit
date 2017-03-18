package(default_visibility = ["//visibility:public"])

load(
    "@com_googlesource_gerrit_bazlets//tools:junit.bzl",
    "junit_tests",
)

junit_tests(
    name = "lfs",
    srcs = glob(["tst/**/*.java"]),
    tags = ["lfs"],
    deps = [
        ":helpers",
        "//lib:junit",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
        "//org.eclipse.jgit.lfs:jgit-lfs",
    ],
)

java_library(
    name = "helpers",
    testonly = 1,
    srcs = glob(["src/**/*.java"]),
    deps = [
        "//lib:junit",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
        "//org.eclipse.jgit.lfs:jgit-lfs",
    ],
)
