load(
    "@com_googlesource_gerrit_bazlets//tools:genrule2.bzl",
    "genrule2",
)
load(
    "@com_googlesource_gerrit_bazlets//tools:junit.bzl",
    "junit_tests",
)

DEPS = [
    "//lib:bcpkix",
    "//lib:bcprov",
    "//lib:bcutil",
    "//lib:junit",
    "//lib:slf4j-api",
    "//lib:sshd-osgi",
    "//lib:sshd-sftp",
    "//org.eclipse.jgit:jgit",
    "//org.eclipse.jgit.junit:junit",
    "//org.eclipse.jgit.junit.ssh:junit-ssh",
    "//org.eclipse.jgit.ssh.apache:ssh-apache",
]

HELPERS = ["tst/org/eclipse/jgit/internal/signing/ssh/AbstractSshSignatureTest.java"]

junit_tests(
    name = "sshd_apache",
    srcs = glob(
        ["tst/**/*.java"],
        exclude = HELPERS,
    ),
    tags = ["sshd"],
    runtime_deps = [":tst_rsrc"],
    deps = DEPS + [
        ":helpers",
    ],
)

java_library(
    name = "helpers",
    testonly = 1,
    srcs = HELPERS,
    deps = DEPS,
)

genrule2(
    name = "tst_rsrc",
    srcs = glob(["tst-rsrc/**"]),
    outs = ["tst_rsrc.jar"],
    cmd = "tar cf - $(SRCS) | tar -C $$TMP --strip-components=2 -xf - && cd $$TMP && zip -qr $$ROOT/$@ .",
)
