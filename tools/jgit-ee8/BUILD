load("@rules_java//java:defs.bzl", "java_binary")

filegroup(
    name = "renames",
    srcs = ["rules/jakarta-to-javax-renames.properties"],
    visibility = ["//visibility:public"],
)

sh_test(
    name = "generated_srcs_test",
    srcs = ["generated_srcs_test.sh"],
    args = [
        "--module",
        "org.eclipse.jgit.http.server/src/",
        "$(location //org.eclipse.jgit.http.server.ee8:jgit-http-server-ee8-srcs)",
        "$(locations //org.eclipse.jgit.http.server:srcs)",
        "--module",
        "org.eclipse.jgit.lfs.server/src/",
        "$(location //org.eclipse.jgit.lfs.server.ee8:jgit-lfs-server-ee8-srcs)",
        "$(locations //org.eclipse.jgit.lfs.server:srcs)",
    ],
    data = [
        "//org.eclipse.jgit.http.server.ee8:jgit-http-server-ee8-srcs",
        "//org.eclipse.jgit.http.server:srcs",
        "//org.eclipse.jgit.lfs.server.ee8:jgit-lfs-server-ee8-srcs",
        "//org.eclipse.jgit.lfs.server:srcs",
    ],
)

java_binary(
    name = "transformer",
    main_class = "org.eclipse.transformer.cli.TransformerCLI",
    # Stable-7.4 uses WORKSPACE maven_jar labels instead of master's
    # bzlmod @external_deps label for the same Eclipse Transformer CLI.
    runtime_deps = [
        "//lib:bnd-transform",
        "//lib:commons-cli",
        "//lib:eclipse-transformer",
        "//lib:eclipse-transformer-cli",
        "//lib:eclipse-transformer-jakarta",
        "//lib:slf4j-api",
        "//lib:slf4j-simple",
    ],
)
