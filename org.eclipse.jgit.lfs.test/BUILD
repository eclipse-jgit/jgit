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
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
        "//org.eclipse.jgit.lfs:jgit-lfs",
        "@hamcrest_core//jar",
        "@hamcrest_library//jar",
        "@junit//jar",
    ],
)

java_library(
    name = "helpers",
    srcs = glob(["src/**/*.java"]),
    deps = [
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
        "//org.eclipse.jgit.lfs:jgit-lfs",
        "@junit//jar",
    ],
)
