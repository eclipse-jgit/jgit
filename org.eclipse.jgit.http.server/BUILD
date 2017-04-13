package(default_visibility = ["//visibility:public"])

java_library(
    name = "jgit-servlet",
    srcs = glob(["src/**/*.java"]),
    resource_strip_prefix = "org.eclipse.jgit.http.server/resources",
    resources = glob(["resources/**"]),
    deps = [
        "//lib:servlet-api",
        # We want these deps to be provided_deps
        "//org.eclipse.jgit:jgit",
    ],
)
