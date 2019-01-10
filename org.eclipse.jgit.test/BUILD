load(
    "@com_googlesource_gerrit_bazlets//tools:genrule2.bzl",
    "genrule2",
)
load(":tests.bzl", "tests")

PKG = "tst/org/eclipse/jgit/"

HELPERS = glob(
    ["src/**/*.java"],
    exclude = ["src/org/eclipse/jgit/transport/ssh/*.java"],
) + [PKG + c for c in [
    "api/AbstractRemoteCommandTest.java",
    "diff/AbstractDiffTestCase.java",
    "internal/storage/file/GcTestCase.java",
    "internal/storage/file/PackIndexTestCase.java",
    "internal/storage/file/XInputStream.java",
    "nls/GermanTranslatedBundle.java",
    "nls/MissingPropertyBundle.java",
    "nls/NoPropertiesBundle.java",
    "nls/NonTranslatedBundle.java",
    "revwalk/RevQueueTestCase.java",
    "revwalk/RevWalkTestCase.java",
    "transport/ObjectIdMatcher.java",
    "transport/SpiTransport.java",
    "treewalk/FileTreeIteratorWithTimeControl.java",
    "treewalk/filter/AlwaysCloneTreeFilter.java",
    "test/resources/SampleDataRepositoryTestCase.java",
    "util/CPUTimeStopWatch.java",
    "util/io/Strings.java",
]]

DATA = [
    PKG + "lib/empty.gitindex.dat",
    PKG + "lib/sorttest.gitindex.dat",
]

RESOURCES = glob(["resources/**"])

tests(glob(
    ["tst/**/*.java"],
    exclude = HELPERS + DATA,
))

java_library(
    name = "helpers",
    testonly = 1,
    srcs = HELPERS,
    resources = DATA,
    deps = [
        "//lib:jsch",
        "//lib:junit",
        "//lib:mockito",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
    ],
)

java_library(
    name = "sshd-helpers",
    testonly = 1,
    srcs = glob(["src/org/eclipse/jgit/transport/ssh/*.java"]),
    resource_strip_prefix = "org.eclipse.jgit.test/resources",
    resources = RESOURCES,
    visibility = [
        "//org.eclipse.jgit.ssh.apache.test:__pkg__",
    ],
    deps = [
        "//lib:jsch",
        "//lib:junit",
        "//lib:sshd-core",
        "//lib:sshd-sftp",
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
        "//org.eclipse.jgit.junit.ssh:junit-ssh",
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
