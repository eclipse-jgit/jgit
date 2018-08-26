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
        "java_toolchain": ":toolchain_vanilla",
    },
)

config_setting(
    name = "java11",
    values = {
        "java_toolchain": ":toolchain_vanilla",
    },
)

java_runtime(
    name = "absolute_javabase",
    java_home = "$(ABSOLUTE_JAVABASE)",
    visibility = ["//visibility:public"],
)

# TODO(davido): Switch to consuming it from @bazel_tool//tools/jdk:toolchain_vanilla
# when my change is included in released Bazel version:
# https://github.com/bazelbuild/bazel/commit/0bef68e054eccecd690e5d9f46db8a0c4b2d887a
default_java_toolchain(
    name = "toolchain_vanilla",
    forcibly_disable_header_compilation = True,
    javabuilder = ["@bazel_tools//tools/jdk:VanillaJavaBuilder_deploy.jar"],
    [],
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
