load("//tools:bzl/jmh.bzl", "jmh_java_benchmarks")

SRCS = glob(
    ["src/**/*.java"],
)

jmh_java_benchmarks(
    name = "benchmarks",
    srcs = SRCS,
    deps = [
        "//org.eclipse.jgit:jgit",
    ],
)
