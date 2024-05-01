load("//tools:bzl/jmh.bzl", "jmh_java_benchmarks")

SRCS = glob(
    ["src/**/*.java"],
)

jmh_java_benchmarks(
    name = "benchmarks",
    srcs = SRCS,
    testonly = 1,
    deps = [
        "//lib:javaewah",
        "//lib:slf4j-api",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
    ],
)
