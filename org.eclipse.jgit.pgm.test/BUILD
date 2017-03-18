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
        "//lib:commons-compress",
        "//lib:javaewah",
        "//lib:junit",
        "//lib:slf4j-api",
        "//lib:slf4j-simple",
        "//lib:xz",
        "//org.eclipse.jgit.archive:jgit-archive",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
        "//org.eclipse.jgit.pgm:pgm",
    ],
)

java_library(
    name = "helpers",
    testonly = 1,
    srcs = glob(["src/**/*.java"]),
    deps = [
        "//lib:args4j",
        "//lib:junit",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
        "//org.eclipse.jgit.pgm:pgm",
    ],
)
