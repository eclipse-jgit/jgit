load("@rules_java//java:defs.bzl", "java_binary")
load("@rules_shell//shell:sh_test.bzl", "sh_test")

filegroup(
    name = "renames",
    srcs = ["rules/jakarta-to-javax-renames.properties"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "test-renames",
    srcs = ["rules/jakarta-to-javax-test-renames.properties"],
    visibility = [
        "//org.eclipse.jgit.http.test.ee8:__pkg__",
        "//org.eclipse.jgit.junit.http:__pkg__",
        "//org.eclipse.jgit.lfs.server.test.ee8:__pkg__",
    ],
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
        "--module",
        "org.eclipse.jgit.junit.http/src/",
        "$(location //org.eclipse.jgit.junit.http:junit-http-ee8-srcs)",
        "$(locations //org.eclipse.jgit.junit.http:ee8-transform-srcs)",
        "--module",
        "org.eclipse.jgit.http.test/src/",
        "$(location //org.eclipse.jgit.http.test.ee8:helpers-srcs)",
        "$(locations //org.eclipse.jgit.http.test:helper-srcs)",
        "--module",
        "org.eclipse.jgit.http.test/tst/",
        "$(location //org.eclipse.jgit.http.test.ee8:test-srcs)",
        "$(locations //org.eclipse.jgit.http.test:test-srcs)",
        "--module",
        "org.eclipse.jgit.lfs.server.test/tst/",
        "$(location //org.eclipse.jgit.lfs.server.test.ee8:test-srcs)",
        "$(locations //org.eclipse.jgit.lfs.server.test:test-srcs)",
    ],
    data = [
        "//org.eclipse.jgit.http.server.ee8:jgit-http-server-ee8-srcs",
        "//org.eclipse.jgit.http.server:srcs",
        "//org.eclipse.jgit.lfs.server.ee8:jgit-lfs-server-ee8-srcs",
        "//org.eclipse.jgit.lfs.server:srcs",
        "//org.eclipse.jgit.junit.http:junit-http-ee8-srcs",
        "//org.eclipse.jgit.junit.http:ee8-transform-srcs",
        "//org.eclipse.jgit.http.test.ee8:helpers-srcs",
        "//org.eclipse.jgit.http.test:helper-srcs",
        "//org.eclipse.jgit.http.test.ee8:test-srcs",
        "//org.eclipse.jgit.http.test:test-srcs",
        "//org.eclipse.jgit.lfs.server.test.ee8:test-srcs",
        "//org.eclipse.jgit.lfs.server.test:test-srcs",
    ],
)

java_binary(
    name = "transformer",
    main_class = "org.eclipse.transformer.cli.TransformerCLI",
    runtime_deps = ["@external_deps//:org_eclipse_transformer_org_eclipse_transformer_cli"],
)
