workspace(name = "jgit")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "bazel_skylib",
    sha256 = "bbccf674aa441c266df9894182d80de104cabd19be98be002f6d478aaa31574d",
    strip_prefix = "bazel-skylib-2169ae1c374aab4a09aa90e65efe1a3aad4e279b",
    urls = ["https://github.com/bazelbuild/bazel-skylib/archive/2169ae1c374aab4a09aa90e65efe1a3aad4e279b.tar.gz"],
)

load("@bazel_skylib//lib:versions.bzl", "versions")

versions.check(minimum_bazel_version = "0.19.0")

load("//tools:bazlets.bzl", "load_bazlets")

load_bazlets(commit = "3afbeab55ece585dbfc7a980bf7214b24ddbbe86")

load(
    "@com_googlesource_gerrit_bazlets//tools:maven_jar.bzl",
    "maven_jar",
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
    artifact = "com.googlecode.javaewah:JavaEWAH:1.1.6",
    sha1 = "94ad16d728b374d65bd897625f3fbb3da223a2b6",
)

maven_jar(
    name = "httpclient",
    artifact = "org.apache.httpcomponents:httpclient:4.5.6",
    sha1 = "1afe5621985efe90a92d0fbc9be86271efbe796f",
)

maven_jar(
    name = "httpcore",
    artifact = "org.apache.httpcomponents:httpcore:4.4.10",
    sha1 = "acc54d9b28bdffe4bbde89ed2e4a1e86b5285e2b",
)

maven_jar(
    name = "sshd-core",
    artifact = "org.apache.sshd:sshd-core:2.0.0",
    sha1 = "f4275079a2463cfd2bf1548a80e1683288a8e86b",
)

maven_jar(
    name = "sshd-sftp",
    artifact = "org.apache.sshd:sshd-sftp:2.0.0",
    sha1 = "a12d64dc2d5d23271a4dc58075e55f9c64a68494",
)

maven_jar(
    name = "commons-codec",
    artifact = "commons-codec:commons-codec:1.10",
    sha1 = "4b95f4897fa13f2cd904aee711aeafc0c5295cd8",
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
    name = "servlet-api-3_1",
    artifact = "javax.servlet:javax.servlet-api:3.1.0",
    sha1 = "3cd63d075497751784b2fa84be59432f4905bf7c",
)

maven_jar(
    name = "commons-compress",
    artifact = "org.apache.commons:commons-compress:1.18",
    sha1 = "1191f9f2bc0c47a8cce69193feb1ff0a8bcb37d5",
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
    artifact = "junit:junit:4.12",
    sha1 = "2973d150c0dc1fefe998f834810d68f278ea58ec",
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
    artifact = "org.mockito:mockito-core:2.13.0",
    sha1 = "8e372943974e4a121fb8617baced8ebfe46d54f0",
)

BYTE_BUDDY_VERSION = "1.7.9"

maven_jar(
    name = "byte_buddy",
    artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VERSION,
    sha1 = "51218a01a882c04d0aba8c028179cce488bbcb58",
)

maven_jar(
    name = "byte_buddy_agent",
    artifact = "net.bytebuddy:byte-buddy-agent:" + BYTE_BUDDY_VERSION,
    sha1 = "a6c65f9da7f467ee1f02ff2841ffd3155aee2fc9",
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

JETTY_VER = "9.4.14.v20181114"

maven_jar(
    name = "jetty-servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VER,
    sha1 = "96f501462af425190ff7b63e387692c1aa3af2c8",
    src_sha1 = "204b8a84adf3ce354138509c42638b5b2d223d1f",
)

maven_jar(
    name = "jetty-security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VER,
    sha1 = "6cbeb2fe9b3cc4f88a7ea040b8a0c4f703cd72ce",
    src_sha1 = "33555125c5988fca12273f60a0aa545d848de54a",
)

maven_jar(
    name = "jetty-server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VER,
    sha1 = "b36a3d52d78a1df6406f6fa236a6eeff48cbfef6",
    src_sha1 = "55db20ea68c9c1b0ed264d80e7d75b4988da87a6",
)

maven_jar(
    name = "jetty-http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VER,
    sha1 = "6d0c8ac42e9894ae7b5032438eb4579c2a47f4fe",
    src_sha1 = "3c4f8a942909cabe6d029f835c185207eb91af75",
)

maven_jar(
    name = "jetty-io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VER,
    sha1 = "a8c6a705ddb9f83a75777d89b0be59fcef3f7637",
    src_sha1 = "06132108ccabbe181707af911e5a68fd8e8806ff",
)

maven_jar(
    name = "jetty-util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VER,
    sha1 = "5bb3d7a38f7ea54138336591d89dd5867b806c02",
    src_sha1 = "94e89a8c9f82e38555e95b9f7f58344a247e862c",
)

BOUNCYCASTLE_VER = "1.60"

maven_jar(
    name = "bcpg-jdk15on",
    artifact = "org.bouncycastle:bcpg-jdk15on:" + BOUNCYCASTLE_VER,
    sha1 = "13c7a199c484127daad298996e95818478431a2c",
    src_sha1 = "edcd9e86d95e39b4da39bb295efd93bc4f56266e",
)

maven_jar(
    name = "bcprov-jdk15on",
    artifact = "org.bouncycastle:bcprov-jdk15on:" + BOUNCYCASTLE_VER,
    sha1 = "bd47ad3bd14b8e82595c7adaa143501e60842a84",
    src_sha1 = "7c57a4d13fe53d9abb967bba600dd0b293dafd6a",
)

maven_jar(
    name = "bcpkix-jdk15on",
    artifact = "org.bouncycastle:bcpkix-jdk15on:" + BOUNCYCASTLE_VER,
    sha1 = "d0c46320fbc07be3a24eb13a56cee4e3d38e0c75",
    src_sha1 = "a25f041293f401af08efba63ff4bbdce98134a03",
)
