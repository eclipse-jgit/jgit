load("@rules_java//java:defs.bzl", "java_library")
load("@com_googlesource_gerrit_bazlets//tools:servlet_transform.bzl", "transform_srcjar")

package(default_visibility = ["//visibility:public"])

transform_srcjar(
    name = "jgit-http-server-ee8-srcs",
    direction = "to_javax",
    sources = ["//org.eclipse.jgit.http.server:srcs"],
    src_prefix = "org.eclipse.jgit.http.server/src/",
)

filegroup(
    name = "jgit-servlet-ee8-srcjar",
    srcs = [":jgit-http-server-ee8-srcs"],
)

java_library(
    name = "jgit-servlet-ee8",
    srcs = [":jgit-http-server-ee8-srcs"],
    resource_strip_prefix = "org.eclipse.jgit.http.server/resources",
    resources = ["//org.eclipse.jgit.http.server:jgit-servlet-resources"],
    deps = [
        "//lib:javax-servlet-api",
        "//org.eclipse.jgit:jgit",
    ],
)
