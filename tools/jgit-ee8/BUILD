load("@rules_java//java:defs.bzl", "java_binary")
load("@rules_shell//shell:sh_test.bzl", "sh_test")

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
        "//org.eclipse.jgit.http.server:srcs",
        "//org.eclipse.jgit.http.server.ee8:jgit-http-server-ee8-srcs",
        "//org.eclipse.jgit.lfs.server:srcs",
        "//org.eclipse.jgit.lfs.server.ee8:jgit-lfs-server-ee8-srcs",
    ],
)

java_binary(
    name = "transformer",
    main_class = "org.eclipse.transformer.cli.TransformerCLI",
    runtime_deps = ["@external_deps//:org_eclipse_transformer_org_eclipse_transformer_cli"],
)
