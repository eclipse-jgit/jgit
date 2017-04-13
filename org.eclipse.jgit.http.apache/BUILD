package(default_visibility = ["//visibility:public"])

java_library(
    name = "http-apache",
    srcs = glob(["src/**/*.java"]),
    resource_strip_prefix = "org.eclipse.jgit.http.apache/resources",
    resources = glob(["resources/**"]),
    deps = [
        "//lib:httpclient",
        "//lib:httpcore",
        "//org.eclipse.jgit:jgit",
    ],
)
