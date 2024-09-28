load(
    "@com_googlesource_gerrit_bazlets//tools:genrule2.bzl",
    "genrule2",
)
load(
    "@com_googlesource_gerrit_bazlets//tools:junit.bzl",
    "junit_tests",
)

junit_tests(
    name = "sshd_apache",
    srcs = glob(["tst/**/*.java"]),
    tags = ["sshd"],
    runtime_deps = [":tst_rsrc"],
    deps = [
        "//lib:eddsa",
        "//lib:junit",
        "//lib:sshd-osgi",
        "//lib:sshd-sftp",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit.ssh:junit-ssh",
        "//org.eclipse.jgit.ssh.apache:ssh-apache",
    ],
)

genrule2(
    name = "tst_rsrc",
    srcs = glob(["tst-rsrc/**"]),
    outs = ["tst_rsrc.jar"],
    cmd = "tar cf - $(SRCS) | tar -C $$TMP --strip-components=2 -xf - && cd $$TMP && zip -qr $$ROOT/$@ .",
)
