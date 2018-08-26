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
    name = "java10",
    values = {
        "java_toolchain": "@bazel_tools//tools/jdk:toolchain_java10",
    },
)

config_setting(
    name = "java11",
    values = {
        "java_toolchain": ":toolchain_vanilla",
    },
)

java_runtime(
    name = "jdk11",
    java_home = "/usr/lib64/jvm/java-11",
    visibility = ["//visibility:public"],
)

filegroup(
    name = "vanillajavabuilder",
    srcs = ["@bazel_tools//tools/jdk:VanillaJavaBuilder_deploy.jar"],
)

default_java_toolchain(
    name = "toolchain_vanilla",
    forcibly_disable_header_compilation = True,
    javabuilder = [":vanillajavabuilder"],
    jvm_opts = [],
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
