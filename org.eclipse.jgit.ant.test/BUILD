load(
    "@com_googlesource_gerrit_bazlets//tools:junit.bzl",
    "junit_tests",
)

junit_tests(
    name = "ant",
    srcs = glob(["src/**/*.java"]),
    tags = ["ant"],
    deps = [
        "//lib:ant",
        "//lib:junit",
        "//lib:slf4j-simple",
        "//org.eclipse.jgit.ant:jgit-ant",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
    ],
)
