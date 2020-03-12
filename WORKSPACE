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

JETTY_VER = "9.4.25.v20191220"

maven_jar(
    name = "jetty-servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VER,
    sha1 = "bee77d6a4f87dc90d5bc142cbd6cef470ec46aae",
    src_sha1 = "e8b09b6431fc9cfbff588698ac0262a745fe00e4",
)

maven_jar(
    name = "jetty-security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VER,
    sha1 = "593ff5b5dfd5bf973184329f5d1209b9a411ec12",
    src_sha1 = "369f869a13a33d25535db3176a84945e94a3718a",
)

maven_jar(
    name = "jetty-server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VER,
    sha1 = "5b352c9f9135a1c20e4808e5cb1d84fbddfdc460",
    src_sha1 = "0f3acc2abcdb86491a2c37074592860cb1100269",
)

maven_jar(
    name = "jetty-http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VER,
    sha1 = "c3aa7da362f1a492667ce754ba16b2535b793668",
    src_sha1 = "70ef1436dc895eafe2cc24cf59af6e2d2874d963",
)

maven_jar(
    name = "jetty-io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VER,
    sha1 = "3eb34b5481012701de0ea9dfaf2bdf1dbb947b16",
    src_sha1 = "ad129617793088aaf69eab18a13c9bce02cb1195",
)

maven_jar(
    name = "jetty-util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VER,
    sha1 = "fd8b642cc16728f1c36ca6a64653cb1b26ec0232",
    src_sha1 = "c84dc3026cc4aea013dc97b18228756816167745",
)

BOUNCYCASTLE_VER = "1.64"

maven_jar(
    name = "bcpg",
    artifact = "org.bouncycastle:bcpg-jdk15on:" + BOUNCYCASTLE_VER,
    sha1 = "56956a8c63ccadf62e7c678571cf86f30bd84441",
    src_sha1 = "b241337df9516b35637d9be84451e2f03a81d186",
)

maven_jar(
    name = "bcprov",
    artifact = "org.bouncycastle:bcprov-jdk15on:" + BOUNCYCASTLE_VER,
    sha1 = "1467dac1b787b5ad2a18201c0c281df69882259e",
    src_sha1 = "2881bfaf2c15e9e64b62c2a143db90db7a0d6035",
)

maven_jar(
    name = "bcpkix",
    artifact = "org.bouncycastle:bcpkix-jdk15on:" + BOUNCYCASTLE_VER,
    sha1 = "3dac163e20110817d850d17e0444852a6d7d0bd7",
    src_sha1 = "5c87199786c06e1a53adf16b1998386bad52da89",
)
