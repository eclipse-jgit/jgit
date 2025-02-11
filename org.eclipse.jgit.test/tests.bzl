'''
Expose each test as a bazel target
'''
load(
    "@com_googlesource_gerrit_bazlets//tools:junit.bzl",
    "junit_tests",
)

def tests(tests, srcprefix="tst/", extra_tags=[]):
    '''
    Create a target each of the tests

    Each target is the full push (removing srcprefix) replacing directory
    separators with underscores.

    e.g. a test under tst/a/b/c/A.test will become the target
    //org.eclipse.jgit.tests:a_b_c_A

    Args:
      tests: a glob of tests files
      srcprefix: prefix between org.eclipse.jgit.tests and the package
        start
      extra_tags: additional tags to add to the generated targets
    '''
    for src in tests:
        name = src[len(srcprefix):len(src) - len(".java")].replace("/", "_")
        labels = []
        timeout = "moderate"
        if name.startswith("org_eclipse_jgit_"):
            package = name[len("org.eclipse.jgit_"):]
            if package.startswith("internal_storage_"):
                package = package[len("internal.storage_"):]
            index = package.find("_")
            if index > 0:
                labels.append(package[:index])
            else:
                labels.append(index)
        if "lib" not in labels:
            labels.append("lib")

        labels.extend(extra_tags)

        # TODO(http://eclip.se/534285): Make this test pass reliably
        # and remove the flaky attribute.
        flaky = src.endswith("CrissCrossMergeTest.java")

        additional_deps = []
        if src.endswith("RootLocaleTest.java"):
            additional_deps = [
                "//org.eclipse.jgit.pgm:pgm",
                "//org.eclipse.jgit.ui:ui",
            ]
        if src.endswith("WalkEncryptionTest.java"):
            additional_deps = [
                "//org.eclipse.jgit:insecure_cipher_factory",
            ]
        if src.endswith("SecurityManagerMissingPermissionsTest.java"):
            additional_deps = [
                "//lib:slf4j-simple",
            ]
        if src.endswith("JDKHttpConnectionTest.java"):
            additional_deps = [
                "//lib:mockito",
            ]
        if src.endswith("TransportHttpTest.java"):
            additional_deps = [
                "//lib:mockito",
            ]
        if src.endswith("ArchiveCommandTest.java"):
            additional_deps = [
                "//lib:commons-compress",
                "//lib:xz",
                "//org.eclipse.jgit.archive:jgit-archive",
            ]
        if src.endswith("FileRepositoryBuilderAfterOpenConfigTest.java") or \
           src.endswith("RefDirectoryAfterOpenConfigTest.java") or \
           src.endswith("SnapshottingRefDirectoryTest.java"):
            additional_deps = [
                ":base",
            ]
        heap_size = "-Xmx256m"
        if src.endswith("HugeCommitMessageTest.java"):
            heap_size = "-Xmx512m"
        if src.endswith("EolRepositoryTest.java") or src.endswith("GcCommitSelectionTest.java"):
            timeout = "long"

        junit_tests(
            name = name,
            tags = labels,
            srcs = [src],
            deps = additional_deps + [
                ":helpers",
                ":tst_rsrc",
                "//lib:javaewah",
                "//lib:junit",
                "//lib:slf4j-api",
                "//lib:slf4j-simple",
                "//org.eclipse.jgit:jgit",
                "//org.eclipse.jgit.junit:junit",
                "//org.eclipse.jgit.lfs:jgit-lfs",
            ],
            flaky = flaky,
            jvm_flags = [heap_size, "-Dfile.encoding=UTF-8"],
            timeout = timeout,
        )
