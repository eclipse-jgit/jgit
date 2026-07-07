load("@rules_java//java:defs.bzl", "java_library")
load("@com_googlesource_gerrit_bazlets//tools:servlet_transform.bzl", "transform_srcjar")

package(default_visibility = ["//visibility:public"])

transform_srcjar(
    name = "jgit-lfs-server-ee8-srcs",
    direction = "to_javax",
    sources = ["//org.eclipse.jgit.lfs.server:srcs"],
    src_prefix = "org.eclipse.jgit.lfs.server/src/",
)

filegroup(
    name = "jgit-lfs-server-ee8-srcjar",
    srcs = [":jgit-lfs-server-ee8-srcs"],
)

java_library(
    name = "jgit-lfs-server-ee8",
    srcs = [":jgit-lfs-server-ee8-srcs"],
    resource_strip_prefix = "org.eclipse.jgit.lfs.server/resources",
    resources = ["//org.eclipse.jgit.lfs.server:jgit-lfs-server-resources"],
    deps = [
        "//lib:gson",
        "//lib:httpcore",
        "//lib:javax-servlet-api",
        "//lib:slf4j-api",
        "//org.eclipse.jgit.http.apache:http-apache",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.lfs:jgit-lfs",
    ],
)
