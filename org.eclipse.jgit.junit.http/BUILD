load("@rules_java//java:defs.bzl", "java_library")
load("@com_googlesource_gerrit_bazlets//tools:servlet_transform.bzl", "transform_srcjar")

package(default_visibility = ["//visibility:public"])

JUNIT_HTTP_SRCS = glob(["src/**/*.java"])

JUNIT_HTTP_EE8_TRANSFORM_SRCS = glob(
    ["src/**/*.java"],
    exclude = ["src/org/eclipse/jgit/junit/http/AppServer.java"],
)

filegroup(
    name = "srcs",
    testonly = 1,
    srcs = JUNIT_HTTP_SRCS,
    visibility = [
        "//org.eclipse.jgit.junit.http.ee8:__pkg__",
        "//tools/jgit-ee8:__pkg__",
    ],
)

filegroup(
    name = "ee8-transform-srcs",
    testonly = 1,
    srcs = JUNIT_HTTP_EE8_TRANSFORM_SRCS,
    visibility = ["//tools/jgit-ee8:__pkg__"],
)

java_library(
    name = "junit-http",
    testonly = 1,
    srcs = JUNIT_HTTP_SRCS,
    resources = glob(
        ["resources/**"],
        allow_empty = True,
    ),
    # TODO(davido): we want here provided deps
    deps = [
        "//lib:jakarta-servlet-api",
        "//lib:jetty-http",
        "//lib:jetty-io",
        "//lib:jetty-security",
        "//lib:jetty-server",
        "//lib:jetty-servlet",
        "//lib:jetty-session",
        "//lib:jetty-util",
        "//lib:junit",
        "//lib:slf4j-api",
        "//org.eclipse.jgit.http.server:jgit-servlet",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
    ],
)

transform_srcjar(
    name = "junit-http-ee8-srcs",
    direction = "to_javax",
    sources = JUNIT_HTTP_EE8_TRANSFORM_SRCS,
    src_prefix = "org.eclipse.jgit.junit.http/src/",
    visibility = [
        "//org.eclipse.jgit.junit.http.ee8:__pkg__",
        "//tools/jgit-ee8:__pkg__",
    ],
)
