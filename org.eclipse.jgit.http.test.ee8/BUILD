load("@rules_java//java:defs.bzl", "java_library")
load("//tools/jgit-ee8:ee8_junit.bzl", "ee8_junit_tests")
load("//tools/jgit-ee8:jakarta_to_javax.bzl", "jakarta_to_javax_srcjar")

jakarta_to_javax_srcjar(
    name = "helpers-srcs",
    testonly = 1,
    renames = "//tools/jgit-ee8:test-renames",
    sources = ["//org.eclipse.jgit.http.test:helper-srcs"],
    src_prefix = "org.eclipse.jgit.http.test/src/",
    visibility = ["//tools/jgit-ee8:__pkg__"],
)

jakarta_to_javax_srcjar(
    name = "test-srcs",
    testonly = 1,
    renames = "//tools/jgit-ee8:test-renames",
    sources = ["//org.eclipse.jgit.http.test:test-srcs"],
    src_prefix = "org.eclipse.jgit.http.test/tst/",
    visibility = ["//tools/jgit-ee8:__pkg__"],
)

jakarta_to_javax_srcjar(
    name = "server-unit-test-srcs",
    testonly = 1,
    renames = "//tools/jgit-ee8:test-renames",
    sources = ["//org.eclipse.jgit.http.test:http-server-unit-test-srcs"],
    src_prefix = "org.eclipse.jgit.http.test/tst/",
)

# Fast slice for the server utility tests; the default EE8 suite runs the full
# HTTP test target, which already includes these classes.
ee8_junit_tests(
    name = "server_unit",
    suite_srcs = ["//org.eclipse.jgit.http.test:http-server-unit-test-srcs"],
    tags = ["http"],
    transformed_srcs = [":server-unit-test-srcs"],
    deps = [
        "//lib:junit",
        "//org.eclipse.jgit.http.server.ee8:jgit-servlet-ee8",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
    ],
)

ee8_junit_tests(
    name = "http",
    suite_srcs = ["//org.eclipse.jgit.http.test:test-srcs"],
    tags = ["http"],
    transformed_srcs = [":test-srcs"],
    deps = [
        ":helpers",
        "//lib:commons-logging",
        "//lib:httpcore",
        "//lib:javax-servlet-api",
        "//lib:jetty-http",
        "//lib:jetty-io",
        "//lib:jetty-security",
        "//lib:jetty-security-ee8",
        "//lib:jetty-server",
        "//lib:jetty-servlet-ee8",
        "//lib:jetty-util",
        "//lib:junit",
        "//lib:slf4j-api",
        "//lib:slf4j-simple",
        "//org.eclipse.jgit.http.apache:http-apache",
        "//org.eclipse.jgit.http.server.ee8:jgit-servlet-ee8",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit.http.ee8:junit-http-ee8",
        "//org.eclipse.jgit.junit:junit",
    ],
)

java_library(
    name = "helpers",
    testonly = 1,
    srcs = [":helpers-srcs"],
    deps = [
        "//lib:javax-servlet-api",
        "//lib:junit",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
    ],
)
