load(
    "@com_googlesource_gerrit_bazlets//tools:genrule2.bzl",
    "genrule2",
)
load("@rules_java//java:defs.bzl", "java_import")
load(
    "@com_googlesource_gerrit_bazlets//tools:junit.bzl",
    "junit_tests",
)

junit_tests(
    name = "bc",
    srcs = glob(["tst/**/*.java"]),
    tags = ["bc"],
    deps = [
        "//lib:bcpg",
        "//lib:bcprov",
        "//lib:junit",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.gpg.bc:gpg-bc",
        "//org.eclipse.jgit.gpg.bc.test:tst_rsrc",
    ],
)

java_import(
    name = "tst_rsrc",
    jars = [":tst_rsrc_jar"],
)

genrule2(
    name = "tst_rsrc_jar",
    srcs = glob(["tst-rsrc/**"]),
    outs = ["tst_rsrc.jar"],
    cmd = "o=$$PWD/$@ && tar cf - $(SRCS) | tar -C $$TMP --strip-components=2 -xf - && cd  $$TMP && zip -qr $$o .",
)
