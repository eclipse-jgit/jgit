load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

INSECURE_CIPHER_FACTORY = [
    "src/org/eclipse/jgit/transport/InsecureCipherFactory.java",
]

SRCS = glob(
    ["src/**/*.java"],
    exclude = INSECURE_CIPHER_FACTORY,
)

RESOURCES = glob(["resources/**"])

java_library(
    name = "jgit_non_stamped",
    srcs = SRCS,
    resource_strip_prefix = "org.eclipse.jgit/resources",
    resources = RESOURCES,
    javacopts = ["-XepDisableAllChecks"],
    deps = [
        ":insecure_cipher_factory",
        "//lib:javaewah",
        "//lib:slf4j-api",
    ],
)

genrule(
    name = "jgit",
    srcs = [":jgit_non_stamped"],
    outs = ["jgit.jar"],
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
    visibility = ["//visibility:public"],
)

java_library(
    name = "insecure_cipher_factory",
    srcs = INSECURE_CIPHER_FACTORY,
    javacopts = ["-Xep:InsecureCryptoUsage:OFF"],
)
