workspace(name = "jgit")

load("//tools:bazlets.bzl", "load_bazlets")

load_bazlets(commit = "3afbeab55ece585dbfc7a980bf7214b24ddbbe86")

load(
    "@com_googlesource_gerrit_bazlets//tools:maven_jar.bzl",
    "maven_jar",
)

maven_jar(
    name = "jsch",
    artifact = "com.jcraft:jsch:0.1.53",
    sha1 = "658b682d5c817b27ae795637dfec047c63d29935",
)

maven_jar(
    name = "javaewah",
    artifact = "com.googlecode.javaewah:JavaEWAH:1.1.6",
    sha1 = "94ad16d728b374d65bd897625f3fbb3da223a2b6",
)

maven_jar(
    name = "httpclient",
    artifact = "org.apache.httpcomponents:httpclient:4.3.6",
    sha1 = "4c47155e3e6c9a41a28db36680b828ced53b8af4",
)

maven_jar(
    name = "httpcore",
    artifact = "org.apache.httpcomponents:httpcore:4.3.3",
    sha1 = "f91b7a4aadc5cf486df6e4634748d7dd7a73f06d",
)

maven_jar(
    name = "commons_codec",
    artifact = "commons-codec:commons-codec:1.4",
    sha1 = "4216af16d38465bbab0f3dff8efa14204f7a399a",
)

maven_jar(
    name = "commons_logging",
    artifact = "commons-logging:commons-logging:1.1.3",
    sha1 = "f6f66e966c70a83ffbdb6f17a0919eaf7c8aca7f",
)

maven_jar(
    name = "log_api",
    artifact = "org.slf4j:slf4j-api:1.7.2",
    sha1 = "0081d61b7f33ebeab314e07de0cc596f8e858d97",
)

maven_jar(
    name = "slf4j_simple",
    artifact = "org.slf4j:slf4j-simple:1.7.2",
    sha1 = "760055906d7353ba4f7ce1b8908bc6b2e91f39fa",
)

maven_jar(
    name = "servlet_api_3_1",
    artifact = "javax.servlet:javax.servlet-api:3.1.0",
    sha1 = "3cd63d075497751784b2fa84be59432f4905bf7c",
)

maven_jar(
    name = "commons_compress",
    artifact = "org.apache.commons:commons-compress:1.6",
    sha1 = "c7d9b580aff9e9f1998361f16578e63e5c064699",
)

maven_jar(
    name = "tukaani_xz",
    artifact = "org.tukaani:xz:1.3",
    sha1 = "66db21c8484120cb6a51b5b3ea47b6f383942bec",
)

maven_jar(
    name = "args4j",
    artifact = "args4j:args4j:2.0.15",
    sha1 = "139441471327b9cc6d56436cb2a31e60eb6ed2ba",
)

maven_jar(
    name = "junit",
    artifact = "junit:junit:4.11",
    sha1 = "4e031bb61df09069aeb2bffb4019e7a5034a4ee0",
)

maven_jar(
    name = "hamcrest_library",
    artifact = "org.hamcrest:hamcrest-library:1.3",
    sha1 = "4785a3c21320980282f9f33d0d1264a69040538f",
)

maven_jar(
    name = "hamcrest_core",
    artifact = "org.hamcrest:hamcrest-core:1.3",
    sha1 = "42a25dc3219429f0e5d060061f71acb49bf010a0",
)

maven_jar(
    name = "gson",
    artifact = "com.google.code.gson:gson:2.2.4",
    sha1 = "a60a5e993c98c864010053cb901b7eab25306568",
)

JETTY_VER = "9.3.17.v20170317"

maven_jar(
    name = "jetty_servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VER,
    sha1 = "ed6986b0d0ca7b9b0f9015c9efb80442e3043a8e",
    src_sha1 = "ee6b4784a00a92e5c1b6111033b7ae41ac6052a3",
)

maven_jar(
    name = "jetty_security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VER,
    sha1 = "ca52535569445682d42aaa97c7039442719a0507",
    src_sha1 = "2ff9f4fb18b320fd5a0272a427bacc4d5fe7bc86",
)

maven_jar(
    name = "jetty_server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VER,
    sha1 = "194e9a02e6ba249ef4a3f4bd56b4993087992299",
    src_sha1 = "0c9bd572f530c411592aefb71781ecca0b3719a9",
)

maven_jar(
    name = "jetty_http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VER,
    sha1 = "6c02d728e15d4868486254039c867a1ac3e4a52e",
    src_sha1 = "3c0a2a82792f268631b4fefd77be9f126ec974b1",
)

maven_jar(
    name = "jetty_io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VER,
    sha1 = "756a8cd2a1cbfb84a94973b6332dd3eccd47c0cd",
    src_sha1 = "a9afa99cccb19b441364fa805d027f457cbbb136",
)

maven_jar(
    name = "jetty_util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VER,
    sha1 = "b8512ab02819de01f0f5a5c6026163041f579beb",
    src_sha1 = "96f8e3dcdc3660a5c91f19c46695daa70ac95625",
)
