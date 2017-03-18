load(":tests.bzl", "tests")

PKG = "tst/org/eclipse/jgit/"

HELPERS = glob(["src/**/*.java"]) + [PKG + c for c in [
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

tests(glob(
    ["tst/**/*.java"],
    exclude = HELPERS + DATA +
    # TODO(davido): error prone rejects this compilation unit:
    # [InsecureCryptoUsage] Insecure usage of a crypto API:
    # the transformation is not a compile-time constant expression
    [
        "tst/org/eclipse/jgit/transport/WalkEncryptionTest.java",
    ],
))

java_library(
    name = "helpers",
    srcs = HELPERS,
    resources = DATA,
    deps = [
        "//org.eclipse.jgit:jgit",
        "//org.eclipse.jgit.junit:junit",
        "@junit//jar",
    ],
)

java_import(
    name = "tst_rsrc",
    jars = [":tst_rsrc_jar"],
)

genrule(
    name = "tst_rsrc_jar",
    srcs = glob(["tst-rsrc/**"]),
    outs = ["tst_rsrc_jar.jar"],
    cmd = "r=$$PWD && cd org.eclipse.jgit.test/tst-rsrc && zip -qr $$r/$@ .",
)
