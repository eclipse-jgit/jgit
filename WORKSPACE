workspace(name = "jgit")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("//tools:bazlets.bzl", "load_bazlets")

load_bazlets(commit = "f9c119e45d9a241bee720b7fbd6c7fdbc952da5f")

load(
    "@com_googlesource_gerrit_bazlets//tools:maven_jar.bzl",
    "maven_jar",
)

http_archive(
    name = "rules_java",
    sha256 = "4da3761f6855ad916568e2bfe86213ba6d2637f56b8360538a7fb6125abf6518",
    urls = [
        "https://github.com/bazelbuild/rules_java/releases/download/7.5.0/rules_java-7.5.0.tar.gz",
    ],
)

load("@rules_java//java:repositories.bzl", "rules_java_dependencies", "rules_java_toolchains")

rules_java_dependencies()

http_archive(
    name = "ubuntu2204_jdk17",
    sha256 = "8ea82b81c9707e535ff93ef5349d11e55b2a23c62bcc3b0faaec052144aed87d",
    strip_prefix = "rbe_autoconfig-5.1.0",
    urls = [
        "https://gerrit-bazel.storage.googleapis.com/rbe_autoconfig/v5.1.0.tar.gz",
        "https://github.com/davido/rbe_autoconfig/releases/download/v5.1.0/v5.1.0.tar.gz",
    ],
)

register_toolchains("//tools:error_prone_warnings_toolchain_java11_definition")

register_toolchains("//tools:error_prone_warnings_toolchain_java17_definition")

register_toolchains("//tools:error_prone_warnings_toolchain_java21_definition")

# Order of registering toolchains matters. rules_java toolchains take precedence
# over the custom toolchains, so the default jdk21 toolchain gets picked
# (one without custom package_config). That's why the `rules_java_toolchains()`
# must be called after the `register_toolchain()` invocation.
rules_java_toolchains()

JMH_VERS = "1.37"

maven_jar(
    name = "jmh-core",
    artifact = "org.openjdk.jmh:jmh-core:" + JMH_VERS,
    attach_source = False,
    sha1 = "896f27e49105b35ea1964319c83d12082e7a79ef",
)

maven_jar(
    name = "jmh-annotations",
    artifact = "org.openjdk.jmh:jmh-generator-annprocess:" + JMH_VERS,
    attach_source = False,
    sha1 = "da93888682df163144edf9b13d2b78e54166063a",
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

SSHD_VERS = "2.12.0"

maven_jar(
    name = "sshd-osgi",
    artifact = "org.apache.sshd:sshd-osgi:" + SSHD_VERS,
    sha1 = "32b8de1cbb722ba75bdf9898e0c41d42af00ce57",
)

maven_jar(
    name = "sshd-sftp",
    artifact = "org.apache.sshd:sshd-sftp:" + SSHD_VERS,
    sha1 = "0f96f00a07b186ea62838a6a4122e8f4cad44df6",
)

JNA_VERS = "5.14.0"

maven_jar(
    name = "jna",
    artifact = "net.java.dev.jna:jna:" + JNA_VERS,
    sha1 = "67bf3eaea4f0718cb376a181a629e5f88fa1c9dd",
)

maven_jar(
    name = "jna-platform",
    artifact = "net.java.dev.jna:jna-platform:" + JNA_VERS,
    sha1 = "28934d48aed814f11e4c584da55c49fa7032b31b",
)

maven_jar(
    name = "commons-codec",
    artifact = "commons-codec:commons-codec:1.16.0",
    sha1 = "4e3eb3d79888d76b54e28b350915b5dc3919c9de",
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
    artifact = "org.apache.commons:commons-compress:1.26.0",
    sha1 = "659feffdd12280201c8aacb8f7be94f9a883c824",
)

maven_jar(
    name = "commons-io",
    artifact = "commons-io:commons-io:2.15.1",
    sha1 = "f11560da189ab563a5c8e351941415430e9304ea",
)

maven_jar(
    name = "tukaani-xz",
    artifact = "org.tukaani:xz:1.9",
    sha1 = "1ea4bec1a921180164852c65006d928617bd2caf",
)

maven_jar(
    name = "args4j",
    artifact = "args4j:args4j:2.37",
    sha1 = "244f60c057d72a785227c0562d3560f42a7ea54b",
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
    artifact = "org.mockito:mockito-core:5.10.0",
    sha1 = "b3812fa2ee069f1d0b41c1c0155da79d0e1dcde0",
)

maven_jar(
    name = "assertj-core",
    artifact = "org.assertj:assertj-core:3.25.3",
    sha1 = "792b270e73aa1cfc28fa135be0b95e69ea451432",
)

BYTE_BUDDY_VERSION = "1.14.12"

maven_jar(
    name = "bytebuddy",
    artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VERSION,
    sha1 = "6e37f743dc15a8d7a4feb3eb0025cbc612d5b9e1",
)

maven_jar(
    name = "bytebuddy-agent",
    artifact = "net.bytebuddy:byte-buddy-agent:" + BYTE_BUDDY_VERSION,
    sha1 = "be4984cb6fd1ef1d11f218a648889dfda44b8a15",
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

JETTY_VER = "10.0.20"

maven_jar(
    name = "jetty-servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VER,
    sha1 = "e3661d28d5f2e2185fb085ce5560b2969e8f7c1e",
    src_sha1 = "23b97219833769e3e69a071abab3811f49077e2e",
)

maven_jar(
    name = "jetty-security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VER,
    sha1 = "f082f9198038e4781ff4cdf4d32db9f90fa8f38a",
    src_sha1 = "a1acb8cb40bd495ba67562cb342f371425dba47b",
)

maven_jar(
    name = "jetty-server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VER,
    sha1 = "66deea078236e21ae9dd7443716b7642766526aa",
    src_sha1 = "e2ddea07b6c85dbc7cf078e408f68d69e504b33c",
)

maven_jar(
    name = "jetty-http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VER,
    sha1 = "d76bf933db81e9f530f1e59f1858bceddb7c4a43",
    src_sha1 = "5fd939e4e5000ac76eb686e68e978747802c8a5f",
)

maven_jar(
    name = "jetty-io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VER,
    sha1 = "269cea7949f739e41707f3d574782c9f227fe188",
    src_sha1 = "1d30687503a0256da23706e1ebf597658d8c0011",
)

maven_jar(
    name = "jetty-util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VER,
    sha1 = "c628108266e75f17dffee34191b74a696fe21746",
    src_sha1 = "63c17b20a8ca5c3bc1740bd807ad446f842749b4",
)

maven_jar(
    name = "jetty-util-ajax",
    artifact = "org.eclipse.jetty:jetty-util-ajax:" + JETTY_VER,
    sha1 = "049dcc0ad4190786e17389b257195fe936ed3d27",
    src_sha1 = "b012c053d25d902cc64a5c4432a8eedab3241d94",
)

BOUNCYCASTLE_VER = "1.78.1"

maven_jar(
    name = "bcpg",
    artifact = "org.bouncycastle:bcpg-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "6c8dbcec20355278ec54840e735f63db2479150e",
    src_sha1 = "2ddef60d84dd8c14ebce4c13100f0bc55fed6922",
)

maven_jar(
    name = "bcprov",
    artifact = "org.bouncycastle:bcprov-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "39e9e45359e20998eb79c1828751f94a818d25f8",
    src_sha1 = "70f58ec93da543dda6a21614b768cb2e386fd512",
)

maven_jar(
    name = "bcutil",
    artifact = "org.bouncycastle:bcutil-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "5353ca39fe2f148dab9ca1d637a43d0750456254",
    src_sha1 = "8d2e0747f5d806f39a602f7f91610444d88c4e2c",
)

maven_jar(
    name = "bcpkix",
    artifact = "org.bouncycastle:bcpkix-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "17b3541f736df97465f87d9f5b5dfa4991b37bb3",
    src_sha1 = "3aeaf221772ad0c9c04593688cb86c6eb74d48b9",
)
