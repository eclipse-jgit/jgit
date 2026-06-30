load("@rules_java//java:defs.bzl", "java_library")
load("@com_googlesource_gerrit_bazlets//tools:junit.bzl", "junit_tests")
load("@com_googlesource_gerrit_bazlets//tools:servlet_transform.bzl", "transform_srcjar")

transform_srcjar(
    name = "test-srcs",
    testonly = 1,
    direction = "to_javax",
    sources = ["//org.eclipse.jgit.lfs.server.test:test-srcs"],
    src_prefix = "org.eclipse.jgit.lfs.server.test/tst/",
    visibility = ["//tools/jgit-ee8:__pkg__"],
)

transform_srcjar(
    name = "base-test-srcs",
    testonly = 1,
    direction = "to_javax",
    sources = ["//org.eclipse.jgit.lfs.server.test:base-test-srcs"],
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

junit_tests(
    name = "lfs_server",
    jvm_flags = [
        "-Xmx512m",
        "-Dfile.encoding=UTF-8",
    ],
    suite_srcs = ["//org.eclipse.jgit.lfs.server.test:nonbase-test-srcs"],
    tags = ["lfs-server"],
    srcs = [":test-srcs"],
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
