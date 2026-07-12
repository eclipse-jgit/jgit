load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

JUNIT_HTTP_SRCS = glob(["src/**/*.java"])

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
