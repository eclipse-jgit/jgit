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

SSHD_VERS = "2.13.1"

maven_jar(
    name = "sshd-osgi",
    artifact = "org.apache.sshd:sshd-osgi:" + SSHD_VERS,
    sha1 = "50958cc44076749e790d7332021cff546707624c",
)

maven_jar(
    name = "sshd-sftp",
    artifact = "org.apache.sshd:sshd-sftp:" + SSHD_VERS,
    sha1 = "e1b6da4ef604718e32cad59ef32618610da7a170",
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
    artifact = "commons-codec:commons-codec:1.17.0",
    sha1 = "0dbe8eef6e14460e73da07f7b11bf994d6626355",
)

maven_jar(
    name = "commons-logging",
    artifact = "commons-logging:commons-logging:1.3.2",
    sha1 = "3dc966156ef19d23c839715165435e582fafa753",
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
    artifact = "jakarta.servlet:jakarta.servlet-api:6.0.0",
    sha1 = "abecc699286e65035ebba9844c03931357a6a963",
)

maven_jar(
    name = "commons-compress",
    artifact = "org.apache.commons:commons-compress:1.26.2",
    sha1 = "eb1f823447af685208e684fce84783b43517960c",
)

maven_jar(
    name = "commons-lang3",
    artifact = "org.apache.commons:commons-lang3:3.14.0",
    sha1 = "1ed471194b02f2c6cb734a0cd6f6f107c673afae",
)

maven_jar(
    name = "commons-io",
    artifact = "commons-io:commons-io:2.16.1",
    sha1 = "377d592e740dc77124e0901291dbfaa6810a200e",
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
    artifact = "org.mockito:mockito-core:5.12.0",
    sha1 = "22f8bbaf478e6789164787fa411a3b5ed986e110",
)

maven_jar(
    name = "assertj-core",
    artifact = "org.assertj:assertj-core:3.26.0",
    sha1 = "fc4a3d051da838946f9dffe725efbd06cd7d1eec",
)

BYTE_BUDDY_VERSION = "1.14.16"

maven_jar(
    name = "bytebuddy",
    artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VERSION,
    sha1 = "e1b9a06e131b7d5b183c8ccc7c29b9e5a8dba077",
)

maven_jar(
    name = "bytebuddy-agent",
    artifact = "net.bytebuddy:byte-buddy-agent:" + BYTE_BUDDY_VERSION,
    sha1 = "4a451ee6484abac3a498df0f3b33ed00a6fced4d",
)

maven_jar(
    name = "objenesis",
    artifact = "org.objenesis:objenesis:3.4",
    sha1 = "675cbe121a68019235d27f6c34b4f0ac30e07418",
)

maven_jar(
    name = "gson",
    artifact = "com.google.code.gson:gson:2.11.0",
    sha1 = "527175ca6d81050b53bdd4c457a6d6e017626b0e",
)

JETTY_VER = "12.0.10"

maven_jar(
    name = "jetty-servlet",
    artifact = "org.eclipse.jetty.ee10:jetty-ee10-servlet:" + JETTY_VER,
    sha1 = "a8d087bde78f231084901553bb6829663b4a22f1",
    src_sha1 = "277a23875dd866b12ac09024656b90cb74d0d27f",
)

maven_jar(
    name = "jetty-security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VER,
    sha1 = "a7bd8056fa84439411f60fedd8a94b1983880cf9",
    src_sha1 = "027e27a757f2d23f28ac26f06e9bf2c49a885cfe",
)

maven_jar(
    name = "jetty-server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VER,
    sha1 = "0dcdef1c9ead5040e6b637512ab1794191058a22",
    src_sha1 = "196fa1a06b65a3b6f549a8ac4523cafe59b1cbd6",
)

maven_jar(
    name = "jetty-session",
    artifact = "org.eclipse.jetty:jetty-session:" + JETTY_VER,
    sha1 = "96dd580a79d938b532e8cff47475efbcaa80ea58",
    src_sha1 = "390d3c79b3b9ff14f8361f7235c6a20374ea9b7e",
)

maven_jar(
    name = "jetty-http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VER,
    sha1 = "71f438b9cf1ba0b3411c5eddd386eeeeca25168a",
    src_sha1 = "cd8b7d81dc839d40bf3dfd2dd2d82731b2a32a67",
)

maven_jar(
    name = "jetty-io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VER,
    sha1 = "4d5ddfc6ad4d6504531861806fade68c7d477f1b",
    src_sha1 = "36f008ab135171e5ebbee18351f16a4eca7a678e",
)

maven_jar(
    name = "jetty-util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VER,
    sha1 = "ec7c95b5a289560f4a7be57170f4fdbedde72b92",
    src_sha1 = "d5c1d7e9723514f5dae9dd89231c4c68129e2a64",
)

maven_jar(
    name = "jetty-util-ajax",
    artifact = "org.eclipse.jetty:jetty-util-ajax:" + JETTY_VER,
    sha1 = "343c9c07e3ff461209a4eb7787dca665b5313818",
    src_sha1 = "4f11df356220d181fd9a6dbb44f18a739d8418a1",
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
