package(default_visibility = ["//visibility:public"])

java_library(
    name = "jgit-lfs-server",
    srcs = glob(["src/**/*.java"]),
    resource_strip_prefix = "org.eclipse.jgit.lfs.server/resources",
    resources = glob(["resources/**"]),
    deps = [
        "//lib:gson",
        "//lib:httpcore",
        "//lib:servlet-api",
        "//lib:slf4j-api",
        "//org.eclipse.jgit.http.apache:http-apache",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.lfs:jgit-lfs",
    ],
)
