java_library(
    name = "pgm",
    srcs = glob(["src/**/*.java"]),
    resource_strip_prefix = "org.eclipse.jgit.pgm/resources",
    resources = glob(["resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        ":services",
        "//lib:args4j",
        "//lib:httpclient",
        "//lib:httpcore",
        "//lib:jetty-http",
        "//lib:jetty-io",
        "//lib:jetty-security",
        "//lib:jetty-server",
        "//lib:jetty-servlet",
        "//lib:jetty-util",
        "//lib:servlet-api",
        "//org.eclipse.jgit.archive:jgit-archive",
        "//org.eclipse.jgit.http.apache:http-apache",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.lfs:jgit-lfs",
        "//org.eclipse.jgit.lfs.server:jgit-lfs-server",
        "//org.eclipse.jgit.ui:ui",
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
