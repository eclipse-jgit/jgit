load("@rules_java//java:defs.bzl", "java_library")
load("//tools/jgit-ee8:ee8_junit.bzl", "ee8_junit_tests")
load("//tools/jgit-ee8:jakarta_to_javax.bzl", "jakarta_to_javax_srcjar")

jakarta_to_javax_srcjar(
    name = "test-srcs",
    testonly = 1,
    sources = ["//org.eclipse.jgit.lfs.server.test:test-srcs"],
    renames = "//tools/jgit-ee8:test-renames",
    src_prefix = "org.eclipse.jgit.lfs.server.test/tst/",
    visibility = ["//tools/jgit-ee8:__pkg__"],
)

jakarta_to_javax_srcjar(
    name = "base-test-srcs",
    testonly = 1,
    sources = ["//org.eclipse.jgit.lfs.server.test:base-test-srcs"],
    renames = "//tools/jgit-ee8:test-renames",
    src_prefix = "org.eclipse.jgit.lfs.server.test/tst/",
)

# Keep the helper/base class separate: the canonical LfsServerTest is compiled
# into the helper jar, while the remaining tests are executed from the EE8
# suite target.
DEPS = [
    "//org.eclipse.jgit.lfs.test:helpers",
    "//org.eclipse.jgit:jgit",
    "//org.eclipse.jgit.junit:junit",
    "//org.eclipse.jgit.junit.http.ee8:junit-http-ee8",
    "//org.eclipse.jgit.lfs:jgit-lfs",
    "//org.eclipse.jgit.lfs.server.ee8:jgit-lfs-server-ee8",
    "//lib:commons-logging",
    "//lib:httpcore",
    "//lib:httpclient",
    "//lib:junit",
    "//lib:jetty-http",
    "//lib:jetty-io",
    "//lib:jetty-server",
    "//lib:jetty-servlet-ee8",
    "//lib:jetty-security",
    "//lib:jetty-security-ee8",
    "//lib:jetty-util",
    "//lib:javax-servlet-api",
]

ee8_junit_tests(
    name = "lfs_server",
    suite_srcs = ["//org.eclipse.jgit.lfs.server.test:nonbase-test-srcs"],
    transformed_srcs = [":test-srcs"],
    jvm_flags = [
        "-Xmx512m",
        "-Dfile.encoding=UTF-8",
    ],
    tags = ["lfs-server"],
    deps = DEPS + [
        ":helpers",
    ],
)

java_library(
    name = "helpers",
    testonly = 1,
    srcs = [":base-test-srcs"],
    deps = DEPS,
)
