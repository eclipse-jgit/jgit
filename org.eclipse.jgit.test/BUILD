load(
    "@com_googlesource_gerrit_bazlets//tools:genrule2.bzl",
    "genrule2",
)
load("@rules_java//java:defs.bzl", "java_import", "java_library")
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
    "revwalk/ReachabilityCheckerTestCase.java",
    "revwalk/RevQueueTestCase.java",
    "revwalk/RevWalkTestCase.java",
    "transport/ObjectIdMatcher.java",
    "transport/RequestValidatorTestCase.java",
    "transport/SpiTransport.java",
    "treewalk/filter/AlwaysCloneTreeFilter.java",
    "test/resources/SampleDataRepositoryTestCase.java",
    "util/CPUTimeStopWatch.java",
    "util/http/HttpCookiesMatcher.java",
    "util/io/Strings.java",
]]

DATA = [
    PKG + "lib/empty.gitindex.dat",
    PKG + "lib/sorttest.gitindex.dat",
]

EXCLUDED = [
    PKG + "api/SecurityManagerTest.java",
    PKG + "api/SecurityManagerMissingPermissionsTest.java",
]

RESOURCES = glob(["resources/**"])

tests(tests = glob(
    ["tst/**/*.java"],
    exclude = HELPERS + DATA + EXCLUDED,
))

java_library(
    name = "helpers",
    testonly = 1,
    srcs = HELPERS,
    resources = DATA,
    deps = [
        "//lib:assertj-core",
        "//lib:jsch",
        "//lib:junit",
        "//lib:mockito",
        "//lib:slf4j-simple",
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
        "//org.eclipse.jgit.gpg.bc.test:__pkg__",
        "//org.eclipse.jgit.ssh.apache.test:__pkg__",
    ],
    deps = [
        "//lib:jsch",
        "//lib:junit",
        "//lib:sshd-osgi",
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
