load("@rules_java//java:defs.bzl", "java_binary", "java_import", "java_library")

PGM_RUNTIME_DEPS = [
    ":services",
    "//lib:javaewah",
    "//org.eclipse.jgit.gpg.bc:gpg-bc",
]

PGM_DEPS = [
    "//lib:args4j",
    "//lib:commons-logging",
    "//lib:httpclient",
    "//lib:httpcore",
    "//lib:jakarta-servlet-api",
    "//lib:jetty-http",
    "//lib:jetty-io",
    "//lib:jetty-security",
    "//lib:jetty-server",
    "//lib:jetty-servlet",
    "//lib:jetty-util",
    "//org.eclipse.jgit.archive:jgit-archive",
    "//org.eclipse.jgit.http.apache:http-apache",
    "//org.eclipse.jgit:jgit",
    "//org.eclipse.jgit.lfs:jgit-lfs",
    "//org.eclipse.jgit.lfs.server:jgit-lfs-server",
    "//org.eclipse.jgit.ssh.apache:ssh-apache",
    "//org.eclipse.jgit.ui:ui",
]

java_library(
    name = "pgm",
    srcs = glob(["src/**/*.java"]),
    resource_strip_prefix = "org.eclipse.jgit.pgm/resources",
    resources = glob(["resources/**"]),
    visibility = ["//visibility:public"],
    runtime_deps = PGM_RUNTIME_DEPS,
    deps = PGM_DEPS,
)

java_binary(
    name = "jgit",
    main_class = "org.eclipse.jgit.pgm.Main",
    runtime_deps = [":pgm-stamped"],
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

java_import(
    name = "pgm-stamped",
    jars = [":pgm-stamped-jar"],
    runtime_deps = PGM_DEPS + PGM_RUNTIME_DEPS,
)

genrule(
    name = "pgm-stamped-jar",
    srcs = [":pgm"],
    outs = ["pgm-stamped.jar"],
    cmd = " && ".join([
        "ROOT=$$PWD",
        "TMP=$$(mktemp -d || mktemp -d -t bazel-tmp)",
        "TZ=UTC",
        "export TZ",
        "GEN_VERSION=$$(cat bazel-out/stable-status.txt | grep -w STABLE_BUILD_JGIT_LABEL | cut -d ' ' -f 2)",
        "cd $$TMP",
        "unzip -q $$ROOT/$<",
        "echo \"Implementation-Version: $$GEN_VERSION\n$$(cat META-INF/MANIFEST.MF)\" > META-INF/MANIFEST.MF",
        "find . -exec touch -t 198001010000 '{}' ';'",
        "zip -Xqr $$ROOT/$@ .",
        "rm -rf $$TMP",
    ]),
    stamp = 1,
)
