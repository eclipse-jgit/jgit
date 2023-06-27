workspace(name = "jgit")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("//tools:bazlets.bzl", "load_bazlets")

load_bazlets(commit = "f30a992da9fc855dce819875afb59f9dd6f860cd")

load(
    "@com_googlesource_gerrit_bazlets//tools:maven_jar.bzl",
    "maven_jar",
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

JMH_VERS = "1.36"

maven_jar(
    name = "jmh-core",
    artifact = "org.openjdk.jmh:jmh-core:" + JMH_VERS,
    attach_source = False,
    sha1 = "5a69117788322630fc5f228bc804771335d41b1b",
)

maven_jar(
    name = "jmh-annotations",
    artifact = "org.openjdk.jmh:jmh-generator-annprocess:" + JMH_VERS,
    attach_source = False,
    sha1 = "41c92c483f92b3cce1c01edd849bfd3ffd920cf6",
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
    artifact = "com.googlecode.javaewah:JavaEWAH:1.2.3",
    sha1 = "13a27c856e0c8808cee9a64032c58eee11c3adc9",
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

SSHD_VERS = "2.10.0"

maven_jar(
    name = "sshd-osgi",
    artifact = "org.apache.sshd:sshd-osgi:" + SSHD_VERS,
    sha1 = "03677ac1da780b7bdb682da50b762d79ea0d940d",
)

maven_jar(
    name = "sshd-sftp",
    artifact = "org.apache.sshd:sshd-sftp:" + SSHD_VERS,
    sha1 = "88707339ac0693d48df0ec1bafb84c78d792ed08",
)

maven_jar(
    name = "jna",
    artifact = "net.java.dev.jna:jna:5.13.0",
    sha1 = "1200e7ebeedbe0d10062093f32925a912020e747",
)

maven_jar(
    name = "jna-platform",
    artifact = "net.java.dev.jna:jna-platform:5.13.0",
    sha1 = "88e9a306715e9379f3122415ef4ae759a352640d",
)

maven_jar(
    name = "commons-codec",
    artifact = "commons-codec:commons-codec:1.15",
    sha1 = "49d94806b6e3dc933dacbd8acb0fdbab8ebd1e5d",
)

maven_jar(
    name = "commons-logging",
    artifact = "commons-logging:commons-logging:1.2",
    sha1 = "4bfc12adfe4842bf07b657f0369c4cb522955686",
)

maven_jar(
    name = "log-api",
    artifact = "org.slf4j:slf4j-api:1.7.36",
    sha1 = "6c62681a2f655b49963a5983b8b0950a6120ae14",
)

maven_jar(
    name = "slf4j-simple",
    artifact = "org.slf4j:slf4j-simple:1.7.36",
    sha1 = "a41f9cfe6faafb2eb83a1c7dd2d0dfd844e2a936",
)

maven_jar(
    name = "servlet-api",
    artifact = "jakarta.servlet:jakarta.servlet-api:4.0.4",
    sha1 = "b8a1142e04838fe54194049c6e7a18dae8f9b960",
)

maven_jar(
    name = "commons-compress",
    artifact = "org.apache.commons:commons-compress:1.23.0",
    sha1 = "4af2060ea9b0c8b74f1854c6cafe4d43cfc161fc",
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
    artifact = "org.assertj:assertj-core:3.24.2",
    sha1 = "ebbf338e33f893139459ce5df023115971c2786f",
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

JETTY_VER = "10.0.15"

maven_jar(
    name = "jetty-servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VER,
    sha1 = "17e21100d9eabae2c0f560ab2c1d5f0edfc4a57b",
    src_sha1 = "989ecc16914e7c8f9f78715dd97d0c511d77a99f",
)

maven_jar(
    name = "jetty-security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VER,
    sha1 = "ae9c2fd327090fc749a6656109adf88f84f05854",
    src_sha1 = "1cae575fc9f3d9271507642606603cca7dc753e8",
)

maven_jar(
    name = "jetty-server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VER,
    sha1 = "d1e941f30300d64b122d5346f1599ecaa8e270ba",
    src_sha1 = "7b04c7d3dc702608306935607bf73ac871816010",
)

maven_jar(
    name = "jetty-http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VER,
    sha1 = "53c4702201c33501bc37a982e5325b5f11084a4e",
    src_sha1 = "2cf03c695ea19c1af5668f5c97dac59e3027eb55",
)

maven_jar(
    name = "jetty-io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VER,
    sha1 = "4481d9593bb89c4da016e49463b0d477faca06dc",
    src_sha1 = "c9f241cce63ac929d4b8bd859c761ba83f4a3124",
)

maven_jar(
    name = "jetty-util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VER,
    sha1 = "eb8901d419e83f2f06809e0cdceaf38b06426f01",
    src_sha1 = "757ee2bd100c5bd20aebc7e2fdc4ceb99f23b451",
)

maven_jar(
    name = "jetty-util-ajax",
    artifact = "org.eclipse.jetty:jetty-util-ajax:" + JETTY_VER,
    sha1 = "0cde62dd87845dd6c0c7f07db6c901e7d020653b",
    src_sha1 = "135448f8b3b3b06f7f3312d222992525ae4bdd25",
)

BOUNCYCASTLE_VER = "1.75"

maven_jar(
    name = "bcpg",
    artifact = "org.bouncycastle:bcpg-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "d37fddd467bc3351e4f9a66716f5f3231a3ba540",
    src_sha1 = "41b22ae4e8455f837ac65cfc618a6900ba392747",
)

maven_jar(
    name = "bcprov",
    artifact = "org.bouncycastle:bcprov-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "fd9638f6468e934991c56242d0da2ae38890c2a4",
    src_sha1 = "824c0265a84d1ec214c3513dc14fc7990bb4f70d",
)

maven_jar(
    name = "bcutil",
    artifact = "org.bouncycastle:bcutil-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "0f58f4bbec8a40137bbb04f6174cd164fae0776b",
    src_sha1 = "47713fc5d0766ab47b7dae237869ae102ed7f103",
)

maven_jar(
    name = "bcpkix",
    artifact = "org.bouncycastle:bcpkix-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "5adfef8a71a0933454739264b56283cc73dd2383",
    src_sha1 = "e50746007d8f1ccd9e49180af87811a1e9ce432d",
)
