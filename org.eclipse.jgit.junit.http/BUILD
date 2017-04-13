package(default_visibility = ["//visibility:public"])

java_library(
    name = "junit-http",
    testonly = 1,
    srcs = glob(["src/**/*.java"]),
    resources = glob(["resources/**"]),
    # TODO(davido): we want here provided deps
    deps = [
        "//lib:jetty-http",
        "//lib:jetty-security",
        "//lib:jetty-server",
        "//lib:jetty-servlet",
        "//lib:jetty-util",
        "//lib:junit",
        "//lib:servlet-api",
        "//lib:slf4j-api",
        "//org.eclipse.jgit.http.server:jgit-servlet",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
    ],
)
