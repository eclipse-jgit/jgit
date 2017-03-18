package(default_visibility = ["//visibility:public"])

java_library(
    name = "pgm",
    srcs = glob(["src/**"]),
    resources = glob(["resources/**"]),
    deps = [
        ":services",
        "//org.eclipse.jgit.archive:jgit-archive",
        "//org.eclipse.jgit.http.apache:http-apache",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.lfs:jgit-lfs",
        "//org.eclipse.jgit.lfs.server:jgit-lfs-server",
        "//org.eclipse.jgit.ui:ui",
        "@args4j//jar",
        "@httpclient//jar",
        "@httpcore//jar",
        "@jetty_http//jar",
        "@jetty_io//jar",
        "@jetty_security//jar",
        "@jetty_server//jar",
        "@jetty_servlet//jar",
        "@jetty_util//jar",
        "@servlet_api_3_1//jar",
    ],
)

java_import(
    name = "services",
    jars = [":services_jar"],
)

genrule(
    name = "services_jar",
    srcs = glob(["META-INF/services/*"]),
    outs = ["services_jar.jar"],
    cmd = "r=$$PWD && cd org.eclipse.jgit.pgm && zip -qr $$r/$@ .",
)
