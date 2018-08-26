package(default_visibility = ["//visibility:public"])

load(
    "@bazel_tools//tools/jdk:default_java_toolchain.bzl",
    "default_java_toolchain",
)

config_setting(
    name = "java9",
    values = {
        "java_toolchain": "@bazel_tools//tools/jdk:toolchain_java9",
    },
)

config_setting(
    name = "java_next",
    values = {
        "java_toolchain": ":toolchain_vanilla",
    },
)

genrule(
    name = "all",
    testonly = 1,
    srcs = [
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.pgm:pgm",
        "//org.eclipse.jgit.ui:ui",
        "//org.eclipse.jgit.archive:jgit-archive",
        "//org.eclipse.jgit.http.apache:http-apache",
        "//org.eclipse.jgit.http.server:jgit-servlet",
        "//org.eclipse.jgit.lfs:jgit-lfs",
        "//org.eclipse.jgit.lfs.server:jgit-lfs-server",
        "//org.eclipse.jgit.junit:junit",
    ],
    outs = ["all.zip"],
    cmd = " && ".join([
        "p=$$PWD",
        "t=$$(mktemp -d || mktemp -d -t bazel-tmp)",
        "cp $(SRCS) $$t",
        "cd $$t",
        "zip -qr $$p/$@ .",
    ]),
)
