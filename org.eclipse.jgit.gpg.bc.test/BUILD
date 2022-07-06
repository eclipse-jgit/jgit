load(
    "@com_googlesource_gerrit_bazlets//tools:genrule2.bzl",
    "genrule2",
)
load(
    "@com_googlesource_gerrit_bazlets//tools:junit.bzl",
    "junit_tests",
)

junit_tests(
    name = "bc",
    srcs = glob(["tst/**/*.java"]),
    tags = ["bc"],
    runtime_deps = [":tst_rsrc"],
    deps = [
        "//lib:bcpg",
        "//lib:bcprov",
        "//lib:bcutil",
        "//lib:junit",
        "//org.eclipse.jgit.gpg.bc:gpg-bc",
        "//org.eclipse.jgit:jgit",
    ],
)

genrule2(
    name = "tst_rsrc",
    srcs = glob(["tst-rsrc/**"]),
    outs = ["tst_rsrc.jar"],
    cmd = "tar cf - $(SRCS) | tar -C $$TMP --strip-components=2 -xf - && cd $$TMP && zip -qr $$ROOT/$@ .",
)
