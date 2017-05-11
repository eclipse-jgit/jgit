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

JETTY_VER = "9.4.5.v20170502"

maven_jar(
    name = "jetty_servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VER,
    sha1 = "394a535b76ca7399b25be3266f06f614e020517e",
    src_sha1 = "4e85803c8d539aa0a8389e113095ef86032ac425",
)

maven_jar(
    name = "jetty_security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VER,
    sha1 = "4f4fc4cbe3504b6c91143ee37b38a1f3de2dcc72",
    src_sha1 = "2124a757c87eacea7ad6507be6a415b5b51139b5",
)

maven_jar(
    name = "jetty_server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VER,
    sha1 = "b4d30340213c3d2a5f908860ba170c5a697829be",
    src_sha1 = "295d873f609a0e2863f33b5dbc8906ca348f1107",
)

maven_jar(
    name = "jetty_http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VER,
    sha1 = "c51b8a6a67d64672889249dd958edd77bff8fc0c",
    src_sha1 = "c1bee39aeb565a4f26852b1851192d98ab611dbc",
)

maven_jar(
    name = "jetty_io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VER,
    sha1 = "76086f955d4e943396b8f340fd5bae3ce4da19d9",
    src_sha1 = "8d41e410b2f0dd284a6e199ed08f45ef7ab2acf1",
)

maven_jar(
    name = "jetty_util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VER,
    sha1 = "5fd36dfcf39110b809bd9b20cec62706ab694711",
    src_sha1 = "629fcda1e4eecfd795e24cc07715ab9797970980",
)
