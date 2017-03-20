package(default_visibility = ["//visibility:public"])

java_library(
    name = "http-apache",
    srcs = glob(["src/**"]),
    resource_strip_prefix = "org.eclipse.jgit.http.apache/resources",
    resources = glob(["resources/**"]),
    deps = [
        "//org.eclipse.jgit:jgit",
        "//lib:httpclient",
        "//lib:httpcore",
    ],
)
