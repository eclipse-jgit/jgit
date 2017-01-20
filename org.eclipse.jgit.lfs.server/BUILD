package(default_visibility = ["//visibility:public"])

java_library(
    name = "jgit-lfs-server",
    srcs = glob(["src/**"]),
    resource_strip_prefix = "org.eclipse.jgit.lfs.server/resources",
    resources = glob(["resources/**"]),
    deps = [
        "//org.eclipse.jgit.http.apache:http-apache",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.lfs:jgit-lfs",
        "@gson//jar",
        "@httpcore//jar",
        "@log_api//jar",
        "@servlet_api_3_1//jar",
    ],
)
