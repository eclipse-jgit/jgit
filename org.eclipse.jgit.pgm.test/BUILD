load(
    "@com_googlesource_gerrit_bazlets//tools:junit.bzl",
    "junit_tests",
)

junit_tests(
    name = "pgm",
    srcs = glob(["tst/**/*.java"]),
    jvm_flags = [
        "-Xmx256m",
        "-Dfile.encoding=UTF-8",
    ],
    tags = ["pgm"],
    deps = [
        ":helpers",
        "//org.eclipse.jgit.archive:jgit-archive",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
        "//org.eclipse.jgit.pgm:pgm",
        "@commons_compress//jar",
        "@hamcrest_core//jar",
        "@hamcrest_library//jar",
        "@javaewah//jar",
        "@junit//jar",
        "@log_api//jar",
        "@slf4j_simple//jar",
        "@tukaani_xz//jar",
    ],
)

java_library(
    name = "helpers",
    srcs = glob(["src/**/*.java"]),
    deps = [
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
        "//org.eclipse.jgit.pgm:pgm",
        "@args4j//jar",
        "@junit//jar",
    ],
)
