load(
    "@com_googlesource_gerrit_bazlets//tools:junit.bzl",
    "junit_tests",
)

TEST_BASE = ["tst/org/eclipse/jgit/lfs/server/fs/LfsServerTest.java"]

DEPS = [
    "//org.eclipse.jgit.lfs.test:helpers",
    "//org.eclipse.jgit:jgit",
    "//org.eclipse.jgit.junit:junit",
    "//org.eclipse.jgit.junit.http:junit-http",
    "//org.eclipse.jgit.lfs:jgit-lfs",
    "//org.eclipse.jgit.lfs.server:jgit-lfs-server",
    "@hamcrest_core//jar",
    "@hamcrest_library//jar",
    "@httpcore//jar",
    "@httpclient//jar",
    "@junit//jar",
    "@jetty_http//jar",
    "@jetty_io//jar",
    "@jetty_server//jar",
    "@jetty_servlet//jar",
    "@jetty_security//jar",
    "@jetty_util//jar",
    "@servlet_api_3_1//jar",
    "@commons_logging//jar",
]

junit_tests(
    name = "lfs_server",
    srcs = glob(
        ["tst/**/*.java"],
        exclude = TEST_BASE,
    ),
    jvm_flags = [
        "-Xmx256m",
        "-Dfile.encoding=UTF-8",
    ],
    tags = ["lfs-server"],
    deps = DEPS + [
        ":helpers",
    ],
)

java_library(
    name = "helpers",
    srcs = TEST_BASE,
    deps = DEPS,
)
