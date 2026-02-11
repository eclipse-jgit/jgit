load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "jmh",
    visibility = ["//visibility:public"],
    exports = [
        "@external_deps//:org_openjdk_jmh_jmh_generator_annprocess",
        "@external_deps//:org_openjdk_jmh_jmh_core",
        "@external_deps//:net_sf_jopt_simple_jopt_simple",
        "@external_deps//:org_apache_commons_commons_math3",
    ],
)
