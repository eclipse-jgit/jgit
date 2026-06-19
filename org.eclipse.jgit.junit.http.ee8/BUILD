load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "junit-http-ee8",
    testonly = 1,
    srcs = [
        "//org.eclipse.jgit.junit.http:junit-http-ee8-srcs",
        # AppServer diverges from the canonical Jakarta version and is copied
        # from the servlet-4 branch as a hand-maintained EE8 overlay.
        "src/org/eclipse/jgit/junit/http/AppServer.java",
    ],
    resources = glob(
        ["resources/**"],
        allow_empty = True,
    ),
    deps = [
        "//lib:javax-servlet-api",
        "//lib:jetty-ee8-nested",
        "//lib:jetty-http",
        "//lib:jetty-io",
        "//lib:jetty-security",
        "//lib:jetty-security-ee8",
        "//lib:jetty-server",
        "//lib:jetty-servlet-ee8",
        "//lib:jetty-session",
        "//lib:jetty-util",
        "//lib:junit",
        "//lib:slf4j-api",
        "//org.eclipse.jgit.http.server.ee8:jgit-servlet-ee8",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
    ],
)

test_suite(
    name = "tests",
    tests = [
        "//org.eclipse.jgit.http.test.ee8:http",
        "//org.eclipse.jgit.lfs.server.test.ee8:lfs_server",
    ],
)
