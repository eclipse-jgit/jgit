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
        "//lib:commons-logging",
        "//lib:jetty-http",
        "//lib:jetty-io",
        "//lib:jetty-security",
        "//lib:jetty-server",
        "//lib:jetty-servlet",
        "//lib:jetty-util",
        "//lib:junit",
        "//lib:servlet-api",
        "//lib:slf4j-api",
        "//lib:slf4j-simple",
        "//org.eclipse.jgit.http.apache:http-apache",
        "//org.eclipse.jgit.http.server:jgit-servlet",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit.http:junit-http",
        "//org.eclipse.jgit.junit:junit",
    ],
)

java_library(
    name = "helpers",
    testonly = 1,
    srcs = glob(["src/**/*.java"]),
    deps = [
        "//lib:junit",
        "//lib:servlet-api",
        "//org.eclipse.jgit.http.server:jgit-servlet",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit.http:junit-http",
        "//org.eclipse.jgit.junit:junit",
    ],
)
