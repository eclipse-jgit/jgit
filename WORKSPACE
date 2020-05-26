workspace(name = "jgit")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "bazel_skylib",
    sha256 = "2ea8a5ed2b448baf4a6855d3ce049c4c452a6470b1efd1504fdb7c1c134d220a",
    strip_prefix = "bazel-skylib-0.8.0",
    urls = ["https://github.com/bazelbuild/bazel-skylib/archive/0.8.0.tar.gz"],
)

# Check Bazel version when invoked by Bazel directly
load("//tools:bazelisk_version.bzl", "bazelisk_version")

bazelisk_version(name = "bazelisk_version")

load("@bazelisk_version//:check.bzl", "check_bazel_version")

check_bazel_version()

load("//tools:bazlets.bzl", "load_bazlets")

load_bazlets(commit = "f30a992da9fc855dce819875afb59f9dd6f860cd")

load(
    "@com_googlesource_gerrit_bazlets//tools:maven_jar.bzl",
    "maven_jar",
)

JMH_VERS = "1.21"

maven_jar(
    name = "jmh-core",
    artifact = "org.openjdk.jmh:jmh-core:" + JMH_VERS,
    attach_source = False,
    sha1 = "442447101f63074c61063858033fbfde8a076873",
)

maven_jar(
    name = "jmh-annotations",
    artifact = "org.openjdk.jmh:jmh-generator-annprocess:" + JMH_VERS,
    attach_source = False,
    sha1 = "7aac374614a8a76cad16b91f1a4419d31a7dcda3",
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
    artifact = "com.jcraft:jzlib:1.1.1",
    sha1 = "a1551373315ffc2f96130a0e5704f74e151777ba",
)

maven_jar(
    name = "javaewah",
    artifact = "com.googlecode.javaewah:JavaEWAH:1.1.7",
    sha1 = "570dde3cd706ae10c62fe19b150928cfdb415e87",
)

maven_jar(
    name = "httpclient",
    artifact = "org.apache.httpcomponents:httpclient:4.5.10",
    sha1 = "7ca2e4276f4ef95e4db725a8cd4a1d1e7585b9e5",
)

maven_jar(
    name = "httpcore",
    artifact = "org.apache.httpcomponents:httpcore:4.4.12",
    sha1 = "21ebaf6d532bc350ba95bd81938fa5f0e511c132",
)

maven_jar(
    name = "sshd-osgi",
    artifact = "org.apache.sshd:sshd-osgi:2.4.0",
    sha1 = "fc4551c1eeda35e4671b263297d37d2bca81c4d4",
)

maven_jar(
    name = "sshd-sftp",
    artifact = "org.apache.sshd:sshd-sftp:2.4.0",
    sha1 = "92e1b7d1e19c715efb4a8871d34145da8f87cdb2",
)

maven_jar(
    name = "commons-codec",
    artifact = "commons-codec:commons-codec:1.13",
    sha1 = "3f18e1aa31031d89db6f01ba05d501258ce69d2c",
)

maven_jar(
    name = "commons-logging",
    artifact = "commons-logging:commons-logging:1.2",
    sha1 = "4bfc12adfe4842bf07b657f0369c4cb522955686",
)

maven_jar(
    name = "log-api",
    artifact = "org.slf4j:slf4j-api:1.7.2",
    sha1 = "0081d61b7f33ebeab314e07de0cc596f8e858d97",
)

maven_jar(
    name = "slf4j-simple",
    artifact = "org.slf4j:slf4j-simple:1.7.2",
    sha1 = "760055906d7353ba4f7ce1b8908bc6b2e91f39fa",
)

maven_jar(
    name = "servlet-api",
    artifact = "javax.servlet:javax.servlet-api:3.1.0",
    sha1 = "3cd63d075497751784b2fa84be59432f4905bf7c",
)

maven_jar(
    name = "commons-compress",
    artifact = "org.apache.commons:commons-compress:1.19",
    sha1 = "7e65777fb451ddab6a9c054beb879e521b7eab78",
)

maven_jar(
    name = "tukaani-xz",
    artifact = "org.tukaani:xz:1.8",
    sha1 = "c4f7d054303948eb6a4066194253886c8af07128",
)

maven_jar(
    name = "args4j",
    artifact = "args4j:args4j:2.33",
    sha1 = "bd87a75374a6d6523de82fef51fc3cfe9baf9fc9",
)

maven_jar(
    name = "junit",
    artifact = "junit:junit:4.13",
    sha1 = "e49ccba652b735c93bd6e6f59760d8254cf597dd",
)

maven_jar(
    name = "hamcrest-library",
    artifact = "org.hamcrest:hamcrest-library:1.3",
    sha1 = "4785a3c21320980282f9f33d0d1264a69040538f",
)

maven_jar(
    name = "hamcrest-core",
    artifact = "org.hamcrest:hamcrest-core:1.3",
    sha1 = "42a25dc3219429f0e5d060061f71acb49bf010a0",
)

maven_jar(
    name = "mockito",
    artifact = "org.mockito:mockito-core:2.23.0",
    sha1 = "497ddb32fd5d01f9dbe99a2ec790aeb931dff1b1",
)

maven_jar(
    name = "assertj-core",
    artifact = "org.assertj:assertj-core:3.14.0",
    sha1 = "3b7b0fcac821f3d167764e9926573cd64f78f9e9",
)

BYTE_BUDDY_VERSION = "1.9.0"

maven_jar(
    name = "bytebuddy",
    artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VERSION,
    sha1 = "8cb0d5baae526c9df46ae17693bbba302640538b",
)

maven_jar(
    name = "bytebuddy-agent",
    artifact = "net.bytebuddy:byte-buddy-agent:" + BYTE_BUDDY_VERSION,
    sha1 = "37b5703b4a6290be3fffc63ae9c6bcaaee0ff856",
)

maven_jar(
    name = "objenesis",
    artifact = "org.objenesis:objenesis:2.6",
    sha1 = "639033469776fd37c08358c6b92a4761feb2af4b",
)

maven_jar(
    name = "gson",
    artifact = "com.google.code.gson:gson:2.8.2",
    sha1 = "3edcfe49d2c6053a70a2a47e4e1c2f94998a49cf",
)

JETTY_VER = "9.4.28.v20200408"

maven_jar(
    name = "jetty-servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VER,
    sha1 = "7df27a6d73e3094ad94ea4f32e3e6597cecbdf38",
    src_sha1 = "49da8455dd5760b7c5961df3b1e7d1490ff9723e",
)

maven_jar(
    name = "jetty-security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VER,
    sha1 = "d5fe6851f14d1375e4b4ab1818475bfd929cf517",
    src_sha1 = "204f19ac7e4df9f6f68df1910154d7667ecd78e8",
)

maven_jar(
    name = "jetty-server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VER,
    sha1 = "9c2cbd96426be38b1273ec87ae21e2696688a737",
    src_sha1 = "83454098deb880ecc7168252578f712c06a5504b",
)

maven_jar(
    name = "jetty-http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VER,
    sha1 = "dd56750ea7410c925f1fbae973c0a19cce5a0a68",
    src_sha1 = "1ef8d10cb5ce5694f12650cbb49b31008c673182",
)

maven_jar(
    name = "jetty-io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VER,
    sha1 = "adda6786588a922f834e9c33c7db5f1484310f44",
    src_sha1 = "4e7756e00b97b439d404e6a682bb1cdeb36fc887",
)

maven_jar(
    name = "jetty-util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VER,
    sha1 = "118d2a44721885a04238aee21a5055dc1ab3818a",
    src_sha1 = "e2e6d7c90e4126645d2667014d02f0732c08c948",
)

BOUNCYCASTLE_VER = "1.65"

maven_jar(
    name = "bcpg",
    artifact = "org.bouncycastle:bcpg-jdk15on:" + BOUNCYCASTLE_VER,
    sha1 = "f32fc02cc29c9fdcc35c0de4d16964f01777067c",
    src_sha1 = "508476d5383c7d086b400f5e7c5a8cf4dc8ac4e2",
)

maven_jar(
    name = "bcprov",
    artifact = "org.bouncycastle:bcprov-jdk15on:" + BOUNCYCASTLE_VER,
    sha1 = "320b989112f00a63a3bcfa5a98f31a4f865a20fa",
    src_sha1 = "ef2676604015c183bb29f8e17846cb3aa4b80c24",
)

maven_jar(
    name = "bcpkix",
    artifact = "org.bouncycastle:bcpkix-jdk15on:" + BOUNCYCASTLE_VER,
    sha1 = "c9507d93e4b453320b57d9ac21bdd67d65a00bbc",
    src_sha1 = "16c71e83af43927d20ccad19defcbb0babcbdb26",
)
