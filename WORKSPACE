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

SSHD_VERS = "2.16.0"

maven_jar(
    name = "sshd-osgi",
    artifact = "org.apache.sshd:sshd-osgi:" + SSHD_VERS,
    sha1 = "87cab2aaa6e06c5d48d746e90f0b3635f8c06419",
)

maven_jar(
    name = "sshd-sftp",
    artifact = "org.apache.sshd:sshd-sftp:" + SSHD_VERS,
    sha1 = "09d9e7024535fb4a3f74367ba7e0a2f5093af638",
)

JNA_VERS = "5.18.0"

maven_jar(
    name = "jna",
    artifact = "net.java.dev.jna:jna:" + JNA_VERS,
    sha1 = "a443db2a11ebf84923f1fac12f29d2969e78f58d",
)

maven_jar(
    name = "jna-platform",
    artifact = "net.java.dev.jna:jna-platform:" + JNA_VERS,
    sha1 = "228c7e8be9be28f823b39149e1cf59f9feac7b33",
)

maven_jar(
    name = "commons-codec",
    artifact = "commons-codec:commons-codec:1.19.0",
    sha1 = "8c0dbe3ae883fceda9b50a6c76e745e548073388",
)

maven_jar(
    name = "commons-logging",
    artifact = "commons-logging:commons-logging:1.3.5",
    sha1 = "a3fcc5d3c29b2b03433aa2d2f2d2c1b1638924a1",
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
    artifact = "jakarta.servlet:jakarta.servlet-api:6.1.0",
    sha1 = "1169a246913fe3823782af7943e7a103634867c5",
)

maven_jar(
    name = "commons-compress",
    artifact = "org.apache.commons:commons-compress:1.28.0",
    sha1 = "e482f2c7a88dac3c497e96aa420b6a769f59c8d7",
)

maven_jar(
    name = "commons-lang3",
    artifact = "org.apache.commons:commons-lang3:3.18.0",
    sha1 = "d6524b169a6574cd253760c472d419b47bfd37e6",
)

maven_jar(
    name = "commons-io",
    artifact = "commons-io:commons-io:2.20.0",
    sha1 = "36f3474daec2849c149e877614e7f979b2082cd2",
)

maven_jar(
    name = "tukaani-xz",
    artifact = "org.tukaani:xz:1.10",
    sha1 = "1be8166f89e035a56c6bfc67dbc423996fe577e2",
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
    artifact = "org.mockito:mockito-core:5.19.0",
    sha1 = "82b320935809e3c8ff92c2007dae5dff88d76f32",
)

maven_jar(
    name = "assertj-core",
    artifact = "org.assertj:assertj-core:3.27.4",
    sha1 = "65e62575f7e422df6d290dc1cb15f3c97108b2b1",
)

BYTE_BUDDY_VERSION = "1.17.7"

maven_jar(
    name = "bytebuddy",
    artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VERSION,
    sha1 = "3856bfab61beb23e099a0d6629f2ba8de4b98ace",
)

maven_jar(
    name = "bytebuddy-agent",
    artifact = "net.bytebuddy:byte-buddy-agent:" + BYTE_BUDDY_VERSION,
    sha1 = "fbf3d6d649ed37fc9e9c59480a05be0a26e3c2da",
)

maven_jar(
    name = "objenesis",
    artifact = "org.objenesis:objenesis:3.4",
    sha1 = "675cbe121a68019235d27f6c34b4f0ac30e07418",
)

maven_jar(
    name = "gson",
    artifact = "com.google.code.gson:gson:2.13.2",
    sha1 = "48b8230771e573b54ce6e867a9001e75977fe78e",
)

JETTY_VER = "12.1.0"

maven_jar(
    name = "jetty-servlet",
    artifact = "org.eclipse.jetty.ee10:jetty-ee10-servlet:" + JETTY_VER,
    sha1 = "56c5180952f23a6af4d8f7e34e84a838c7d284cb",
)

maven_jar(
    name = "jetty-security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VER,
    sha1 = "5fb2a02cc342fd132e5a358ecbb4479aabf58a3a",
)

maven_jar(
    name = "jetty-server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VER,
    sha1 = "a53a5d0d03aaeab03908ff854cdd5e9ee256efca",
)

maven_jar(
    name = "jetty-session",
    artifact = "org.eclipse.jetty:jetty-session:" + JETTY_VER,
    sha1 = "be1be354522861b72fdaade01f798ad7f6ffbac4",
)

maven_jar(
    name = "jetty-http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VER,
    sha1 = "d74935bc8eb25780453dfd0a2e93c2729b566e79",
)

maven_jar(
    name = "jetty-io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VER,
    sha1 = "611910afde7e9a2cfda3cad1ec1466cdba55e444",
)

maven_jar(
    name = "jetty-util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VER,
    sha1 = "e0d96b47d0289006941757352c6fe7e5d9370c3b",
)

maven_jar(
    name = "jetty-util-ajax",
    artifact = "org.eclipse.jetty:jetty-util-ajax:" + JETTY_VER,
    sha1 = "26c44a954086af8518fcb311c94c5703f1d3093b",
)

BOUNCYCASTLE_VER = "1.81"

maven_jar(
    name = "bcpg",
    artifact = "org.bouncycastle:bcpg-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "7cd50193dd6a080a203716d993f7a246c8d94291",
)

maven_jar(
    name = "bcprov",
    artifact = "org.bouncycastle:bcprov-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "d17c094daef57dbd80af71687a475aa6df7cbe54",
)

maven_jar(
    name = "bcutil",
    artifact = "org.bouncycastle:bcutil-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "e2dd79395ab435094142b6aba219f35adcba0f01",
)

maven_jar(
    name = "bcpkix",
    artifact = "org.bouncycastle:bcpkix-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "819fd6f5d170c8b8bf8c5acc73816e9c36574042",
)
