workspace(name = "jgit")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("//tools:bazlets.bzl", "load_bazlets")

load_bazlets(commit = "f30a992da9fc855dce819875afb59f9dd6f860cd")

load(
    "@com_googlesource_gerrit_bazlets//tools:maven_jar.bzl",
    "maven_jar",
)

http_archive(
    name = "remote_java_tools",
    sha256 = "0db35ec44745fd15b77d9df954e70a4fcf74554dd5bfe3f6e6cb6bbdc1f1c649",
    urls = [
        "https://mirror.bazel.build/bazel_java_tools/releases/java/v12.1/java_tools-v12.1.zip",
        "https://github.com/bazelbuild/java_tools/releases/download/java_v12.1/java_tools-v12.1.zip",
    ],
)

http_archive(
    name = "remote_java_tools_linux",
    sha256 = "093ecac3b42fcbc3621d08edc3ae3c8b0bc2bf56a0d9a85ddcdb1e0bcf10cbc7",
    urls = [
        "https://mirror.bazel.build/bazel_java_tools/releases/java/v12.1/java_tools_linux-v12.1.zip",
        "https://github.com/bazelbuild/java_tools/releases/download/java_v12.1/java_tools_linux-v12.1.zip",
    ],
)

http_archive(
    name = "remote_java_tools_windows",
    sha256 = "1df7cc7fac54f437f43c24c019462e13058f394fdba5a64f566b92e8af18d0cf",
    urls = [
        "https://mirror.bazel.build/bazel_java_tools/releases/java/v12.1/java_tools_windows-v12.1.zip",
        "https://github.com/bazelbuild/java_tools/releases/download/java_v12.1/java_tools_windows-v12.1.zip",
    ],
)

http_archive(
    name = "remote_java_tools_darwin_x86_64",
    sha256 = "16ca145203a62a1fcd6ae50513c0935d938591cb309b9b1172e257c57873f60d",
    urls = [
        "https://mirror.bazel.build/bazel_java_tools/releases/java/v12.1/java_tools_darwin_x86_64-v12.1.zip",
        "https://github.com/bazelbuild/java_tools/releases/download/java_v12.1/java_tools_darwin_x86_64-v12.1.zip",
    ],
)

http_archive(
    name = "remote_java_tools_darwin_arm64",
    sha256 = "1d8e575e558782c2ceec0940e424f0e2df56b0df3d7fae68333eaceef2c4e41c",
    urls = [
        "https://mirror.bazel.build/bazel_java_tools/releases/java/v12.1/java_tools_darwin_arm64-v12.1.zip",
        "https://github.com/bazelbuild/java_tools/releases/download/java_v12.1/java_tools_darwin_arm64-v12.1.zip",
    ],
)

http_archive(
    name = "rbe_jdk11",
    sha256 = "dbcfd6f26589ef506b91fe03a12dc559ca9c84699e4cf6381150522287f0e6f6",
    strip_prefix = "rbe_autoconfig-3.1.0",
    urls = [
        "https://gerrit-bazel.storage.googleapis.com/rbe_autoconfig/v3.1.0.tar.gz",
        "https://github.com/davido/rbe_autoconfig/archive/v3.1.0.tar.gz",
    ],
)

register_toolchains("//tools:error_prone_warnings_toolchain_java11_definition")

register_toolchains("//tools:error_prone_warnings_toolchain_java17_definition")

JMH_VERS = "1.35"

maven_jar(
    name = "jmh-core",
    artifact = "org.openjdk.jmh:jmh-core:" + JMH_VERS,
    attach_source = False,
    sha1 = "c14d712be8e423969fcd344bc801cf5d3ea3b62a",
)

maven_jar(
    name = "jmh-annotations",
    artifact = "org.openjdk.jmh:jmh-generator-annprocess:" + JMH_VERS,
    attach_source = False,
    sha1 = "50fba446d32d22f95f51a391f3450e03af006754",
)

maven_jar(
    name = "jopt",
    artifact = "net.sf.jopt-simple:jopt-simple:5.0.4",
    attach_source = False,
    sha1 = "4fdac2fbe92dfad86aa6e9301736f6b4342a3f5c",
)

maven_jar(
    name = "math3",
    artifact = "org.apache.commons:commons-math3:3.6.1",
    attach_source = False,
    sha1 = "e4ba98f1d4b3c80ec46392f25e094a6a2e58fcbf",
)

maven_jar(
    name = "eddsa",
    artifact = "net.i2p.crypto:eddsa:0.3.0",
    sha1 = "1901c8d4d8bffb7d79027686cfb91e704217c3e1",
)

maven_jar(
    name = "jsch",
    artifact = "com.jcraft:jsch:0.1.55",
    sha1 = "bbd40e5aa7aa3cfad5db34965456cee738a42a50",
)

maven_jar(
    name = "jzlib",
    artifact = "com.jcraft:jzlib:1.1.3",
    sha1 = "c01428efa717624f7aabf4df319939dda9646b2d",
)

maven_jar(
    name = "javaewah",
    artifact = "com.googlecode.javaewah:JavaEWAH:1.1.13",
    sha1 = "32cd724a42dc73f99ca08453d11a4bb83e0034c7",
)

maven_jar(
    name = "httpclient",
    artifact = "org.apache.httpcomponents:httpclient:4.5.14",
    sha1 = "1194890e6f56ec29177673f2f12d0b8e627dec98",
)

maven_jar(
    name = "httpcore",
    artifact = "org.apache.httpcomponents:httpcore:4.4.16",
    sha1 = "51cf043c87253c9f58b539c9f7e44c8894223850",
)

SSHD_VERS = "2.9.2"

maven_jar(
    name = "sshd-osgi",
    artifact = "org.apache.sshd:sshd-osgi:" + SSHD_VERS,
    sha1 = "bac0415734519b2fe433fea196017acf7ed32660",
)

maven_jar(
    name = "sshd-sftp",
    artifact = "org.apache.sshd:sshd-sftp:" + SSHD_VERS,
    sha1 = "7f9089c87b3b44f19998252fd3b68637e3322920",
)

maven_jar(
    name = "jna",
    artifact = "net.java.dev.jna:jna:5.12.1",
    sha1 = "b1e93a735caea94f503e95e6fe79bf9cdc1e985d",
)

maven_jar(
    name = "jna-platform",
    artifact = "net.java.dev.jna:jna-platform:5.12.1",
    sha1 = "097406a297c852f4a41e688a176ec675f72e8329",
)

maven_jar(
    name = "commons-codec",
    artifact = "commons-codec:commons-codec:1.14",
    sha1 = "3cb1181b2141a7e752f5bdc998b7ef1849f726cf",
)

maven_jar(
    name = "commons-logging",
    artifact = "commons-logging:commons-logging:1.2",
    sha1 = "4bfc12adfe4842bf07b657f0369c4cb522955686",
)

maven_jar(
    name = "log-api",
    artifact = "org.slf4j:slf4j-api:1.7.30",
    sha1 = "b5a4b6d16ab13e34a88fae84c35cd5d68cac922c",
)

maven_jar(
    name = "slf4j-simple",
    artifact = "org.slf4j:slf4j-simple:1.7.30",
    sha1 = "e606eac955f55ecf1d8edcccba04eb8ac98088dd",
)

maven_jar(
    name = "servlet-api",
    artifact = "javax.servlet:javax.servlet-api:4.0.0",
    sha1 = "60200affc2fe0165136ed3690faf00b66aed581a",
)

maven_jar(
    name = "commons-compress",
    artifact = "org.apache.commons:commons-compress:1.22",
    sha1 = "691a8b4e6cf4248c3bc72c8b719337d5cb7359fa",
)

maven_jar(
    name = "tukaani-xz",
    artifact = "org.tukaani:xz:1.9",
    sha1 = "1ea4bec1a921180164852c65006d928617bd2caf",
)

maven_jar(
    name = "args4j",
    artifact = "args4j:args4j:2.33",
    sha1 = "bd87a75374a6d6523de82fef51fc3cfe9baf9fc9",
)

maven_jar(
    name = "junit",
    artifact = "junit:junit:4.13.2",
    sha1 = "8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12",
)

maven_jar(
    name = "hamcrest",
    artifact = "org.hamcrest:hamcrest:2.2",
    sha1 = "1820c0968dba3a11a1b30669bb1f01978a91dedc",
)

maven_jar(
    name = "mockito",
    artifact = "org.mockito:mockito-core:4.8.1",
    sha1 = "d8eb9dec8747d08645347bb8c69088ac83197975",
)

maven_jar(
    name = "assertj-core",
    artifact = "org.assertj:assertj-core:3.20.2",
    sha1 = "66f1f0ebd6db2b24e4a731979171da16ba919cd5",
)

BYTE_BUDDY_VERSION = "1.12.18"

maven_jar(
    name = "bytebuddy",
    artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VERSION,
    sha1 = "875a9c3f29d2f6f499dfd60d76e97a343f9b1233",
)

maven_jar(
    name = "bytebuddy-agent",
    artifact = "net.bytebuddy:byte-buddy-agent:" + BYTE_BUDDY_VERSION,
    sha1 = "417a7310a7bf1c1aae5ca502d26515f9c2f94396",
)

maven_jar(
    name = "objenesis",
    artifact = "org.objenesis:objenesis:3.3",
    sha1 = "1049c09f1de4331e8193e579448d0916d75b7631",
)

maven_jar(
    name = "gson",
    artifact = "com.google.code.gson:gson:2.10.1",
    sha1 = "b3add478d4382b78ea20b1671390a858002feb6c",
)

JETTY_VER = "10.0.13"

maven_jar(
    name = "jetty-servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VER,
    sha1 = "a6ee6e48e98377863aa80f41ea979df678b17966",
    src_sha1 = "5a01db2e1bae632879e9b90e845ae946059b46c9",
)

maven_jar(
    name = "jetty-security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VER,
    sha1 = "6d4c88cf068709d9f2499ca417b23f3f835b0c43",
    src_sha1 = "887e7a7c457e149df9c23db89c7d2425c4444ccf",
)

maven_jar(
    name = "jetty-server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VER,
    sha1 = "f472705ebfce7e9a5b6cb8cbb84e73767e35fad7",
    src_sha1 = "2689f8e616282b19f34d43f89800f67490ae65fa",
)

maven_jar(
    name = "jetty-http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VER,
    sha1 = "b3dc7ec1da090106031dd36cb1e2637a7fb6ce1c",
    src_sha1 = "1bbb620e34218584bfdf11542e2b46781437335d",
)

maven_jar(
    name = "jetty-io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VER,
    sha1 = "be9d7f226022b02e174a83d597d088e22e12d365",
    src_sha1 = "48f5b1ce8570a9d560e62c39170e754288a1d290",
)

maven_jar(
    name = "jetty-util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VER,
    sha1 = "35caf3afb3cca22ca4bc36908bf82e6d973c5be4",
    src_sha1 = "9d7c19deb76c0247ad0d25afce6e4c0d681d2af0",
)

maven_jar(
    name = "jetty-util-ajax",
    artifact = "org.eclipse.jetty:jetty-util-ajax:" + JETTY_VER,
    sha1 = "bc52bc38cb76b5c260ec109661ebcb02393d83a7",
    src_sha1 = "b229198672cfb765ce7571e5e0e855e01170f881",
)

BOUNCYCASTLE_VER = "1.72"

maven_jar(
    name = "bcpg",
    artifact = "org.bouncycastle:bcpg-jdk18on:1.72.2",
    sha1 = "ef29db0e82cf1ee99ddf5d772e810c1beb2d70f1",
    src_sha1 = "72936958f07df15946f4eb6cd2ae558d8d24ed1c",
)

maven_jar(
    name = "bcprov",
    artifact = "org.bouncycastle:bcprov-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "d8dc62c28a3497d29c93fee3e71c00b27dff41b4",
    src_sha1 = "308b5a8a89c29169390210b7b8e2b2534b27ff19",
)

maven_jar(
    name = "bcutil",
    artifact = "org.bouncycastle:bcutil-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "41f19a69ada3b06fa48781120d8bebe1ba955c77",
    src_sha1 = "fc16dc9eb28a2ee6cbe35ecda6ec7e050ddf3cba",
)

maven_jar(
    name = "bcpkix",
    artifact = "org.bouncycastle:bcpkix-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "bb3fdb5162ccd5085e8d7e57fada4d8eaa571f5a",
    src_sha1 = "6fa7015a0be76b270e911bf426abf8efd1c5e42d",
)
