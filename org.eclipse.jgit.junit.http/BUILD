package(default_visibility = ["//visibility:public"])

java_library(
    name = "junit-http",
    srcs = glob(["src/**"]),
    resources = glob(["resources/**"]),
    # TODO(davido): we want here provided deps
    deps = [
        "//org.eclipse.jgit.http.server:jgit-servlet",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
        "@jetty_http//jar",
        "@jetty_security//jar",
        "@jetty_server//jar",
        "@jetty_servlet//jar",
        "@jetty_util//jar",
        "@junit//jar",
        "@log_api//jar",
        "@servlet_api_3_1//jar",
    ],
)
