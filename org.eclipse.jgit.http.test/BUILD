load(
    "@com_googlesource_gerrit_bazlets//tools:junit.bzl",
    "junit_tests",
)

junit_tests(
    name = "http",
    srcs = glob(["tst/**/*.java"]),
    tags = ["http"],
    deps = [
        ":helpers",
        "//org.eclipse.jgit.http.apache:http-apache",
        "//org.eclipse.jgit.http.server:jgit-servlet",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit.http:junit-http",
        "//org.eclipse.jgit.junit:junit",
        "@commons_codec//jar",
        "@commons_logging//jar",
        "@hamcrest_core//jar",
        "@hamcrest_library//jar",
        "@jetty_http//jar",
        "@jetty_io//jar",
        "@jetty_security//jar",
        "@jetty_server//jar",
        "@jetty_servlet//jar",
        "@jetty_util//jar",
        "@junit//jar",
        "@log_api//jar",
        "@servlet_api_3_1//jar",
        "@slf4j_simple//jar",
    ],
)

java_library(
    name = "helpers",
    srcs = glob(["src/**/*.java"]),
    deps = [
        "//org.eclipse.jgit.http.server:jgit-servlet",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit.http:junit-http",
        "//org.eclipse.jgit.junit:junit",
        "@junit//jar",
    ],
)
