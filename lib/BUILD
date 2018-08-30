java_library(
    name = "args4j",
    visibility = [
        "//org.eclipse.jgit.pgm:__pkg__",
        "//org.eclipse.jgit.pgm.test:__pkg__",
    ],
    exports = ["@args4j//jar"],
)

java_library(
    name = "commons-compress",
    visibility = [
        "//org.eclipse.jgit.archive:__pkg__",
        "//org.eclipse.jgit.pgm.test:__pkg__",
    ],
    exports = ["@commons_compress//jar"],
)

java_library(
    name = "commons-codec",
    exports = ["@commons_codec//jar"],
)

java_library(
    name = "commons-logging",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = ["@commons_logging//jar"],
)

java_library(
    name = "gson",
    visibility = ["//org.eclipse.jgit.lfs.server:__pkg__"],
    exports = ["@gson//jar"],
)

java_library(
    name = "httpclient",
    visibility = [
        "//org.eclipse.jgit.http.apache:__pkg__",
        "//org.eclipse.jgit.lfs.server.test:__pkg__",
        "//org.eclipse.jgit.pgm:__pkg__",
    ],
    exports = ["@httpclient//jar"],
)

java_library(
    name = "httpcore",
    visibility = [
        "//org.eclipse.jgit.http.apache:__pkg__",
        "//org.eclipse.jgit.lfs.server:__pkg__",
        "//org.eclipse.jgit.lfs.server.test:__pkg__",
        "//org.eclipse.jgit.pgm:__pkg__",
    ],
    exports = ["@httpcore//jar"],
)

java_library(
    name = "javaewah",
    visibility = ["//visibility:public"],
    exports = ["@javaewah//jar"],
)

java_library(
    name = "jetty-http",
    # TODO: This should be testonly but org.eclipse.jgit.pgm depends on it.
    visibility = ["//visibility:public"],
    exports = ["@jetty_http//jar"],
    runtime_deps = [":commons-codec"],
)

java_library(
    name = "jetty-io",
    # TODO: This should be testonly but org.eclipse.jgit.pgm depends on it.
    visibility = ["//visibility:public"],
    exports = ["@jetty_io//jar"],
)

java_library(
    name = "jetty-security",
    # TODO: This should be testonly but org.eclipse.jgit.pgm depends on it.
    visibility = ["//visibility:public"],
    exports = ["@jetty_security//jar"],
)

java_library(
    name = "jetty-server",
    # TODO: This should be testonly but org.eclipse.jgit.pgm depends on it.
    visibility = ["//visibility:public"],
    exports = ["@jetty_server//jar"],
)

java_library(
    name = "jetty-servlet",
    # TODO: This should be testonly but org.eclipse.jgit.pgm depends on it.
    visibility = ["//visibility:public"],
    exports = ["@jetty_servlet//jar"],
)

java_library(
    name = "jetty-util",
    # TODO: This should be testonly but org.eclipse.jgit.pgm depends on it.
    visibility = ["//visibility:public"],
    exports = ["@jetty_util//jar"],
)

java_library(
    name = "jsch",
    visibility = ["//org.eclipse.jgit:__pkg__"],
    exports = ["@jsch//jar"],
)

java_library(
    name = "junit",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = [
        "@hamcrest_core//jar",
        "@hamcrest_library//jar",
        "@junit//jar",
    ],
)

java_library(
    name = "servlet-api",
    visibility = [
        "//org.eclipse.jgit.http.apache:__pkg__",
        "//org.eclipse.jgit.http.server:__pkg__",
        "//org.eclipse.jgit.http.test:__pkg__",
        "//org.eclipse.jgit.junit.http:__pkg__",
        "//org.eclipse.jgit.lfs.server:__pkg__",
        "//org.eclipse.jgit.lfs.server.test:__pkg__",
        "//org.eclipse.jgit.pgm:__pkg__",
    ],
    exports = ["@servlet_api_3_1//jar"],
)

java_library(
    name = "slf4j-api",
    visibility = ["//visibility:public"],
    exports = ["@log_api//jar"],
)

java_library(
    name = "slf4j-simple",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = ["@slf4j_simple//jar"],
)

java_library(
    name = "xz",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = ["@tukaani_xz//jar"],
)
