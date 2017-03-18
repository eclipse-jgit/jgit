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
    "//lib:commons-logging",
    "//lib:httpcore",
    "//lib:httpclient",
    "//lib:junit",
    "//lib:jetty-http",
    "//lib:jetty-io",
    "//lib:jetty-server",
    "//lib:jetty-servlet",
    "//lib:jetty-security",
    "//lib:jetty-util",
    "//lib:servlet-api",
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
    testonly = 1,
    srcs = TEST_BASE,
    deps = DEPS,
)
