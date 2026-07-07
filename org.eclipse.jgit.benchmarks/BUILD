load("//tools:bzl/jmh.bzl", "jmh_java_benchmarks")

SRCS = glob(
    ["src/**/*.java"],
)

jmh_java_benchmarks(
    name = "benchmarks",
    testonly = 1,
    srcs = SRCS,
    deps = [
        "//lib:javaewah",
        "//lib:junit",
        "//lib:slf4j-api",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
    ],
)
