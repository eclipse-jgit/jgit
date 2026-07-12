load(
    "@com_googlesource_gerrit_bazlets//tools:junit.bzl",
    "junit_tests",
)
load("@rules_java//java:defs.bzl", "java_library")

HTTP_HELPER_SRCS = glob(["src/**/*.java"])

HTTP_TEST_SRCS = glob(["tst/**/*.java"])

HTTP_SERVER_UNIT_TEST_SRCS = [
    "tst/org/eclipse/jgit/http/server/ClientVersionUtilTest.java",
    "tst/org/eclipse/jgit/http/server/RootLocaleTest.java",
    "tst/org/eclipse/jgit/http/server/ServletUtilsTest.java",
]

junit_tests(
    name = "http",
    srcs = HTTP_TEST_SRCS,
    tags = ["http"],
    deps = [
        ":helpers",
        "//lib:commons-logging",
        "//lib:httpcore",
        "//lib:jakarta-servlet-api",
        "//lib:jetty-http",
        "//lib:jetty-io",
        "//lib:jetty-security",
        "//lib:jetty-server",
        "//lib:jetty-servlet",
        "//lib:jetty-util",
        "//lib:junit",
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
    srcs = HTTP_HELPER_SRCS,
    deps = [
        "//lib:jakarta-servlet-api",
        "//lib:junit",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
    ],
)
