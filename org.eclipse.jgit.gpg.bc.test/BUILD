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
    cmd = " && ".join([
        "set -o pipefail",
        "TZ=UTC",
        "export TZ",
        "tar chf - $(SRCS) | tar -C $$TMP --strip-components=2 -xf -",
        "cd $$TMP",
        "find . -exec touch -t 198001010000 '{}' +",
        "find . -mindepth 1 -print | LC_ALL=C sort | zip -Xq $$ROOT/$@ -@",
    ]),
)
