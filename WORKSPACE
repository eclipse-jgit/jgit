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

JETTY_VER = "9.4.1.v20170120"

maven_jar(
    name = "jetty_servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VER,
    sha1 = "406a6edc22311629b41e98ab26901574d243d408",
    src_sha1 = "57cf143c4110e0d43635406a1052013bbf29a4da",
)

maven_jar(
    name = "jetty_security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VER,
    sha1 = "677aa94298f42d322e2f19045693a233613537b6",
    src_sha1 = "933928163506a8d366652fb895e770fcf3f9e8fe",
)

maven_jar(
    name = "jetty_server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VER,
    sha1 = "4cf4bea26592de98b9126ac60bc91fb669176a63",
    src_sha1 = "2035ab117da534f9a525adfaa9f3a5634d202707",
)

maven_jar(
    name = "jetty_http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VER,
    sha1 = "c9c9d5b15b3bb41ae9ae588b7aede301b42186f6",
    src_sha1 = "f1cbf0e1329ed722bf8eba74e4c4b94619dfe8a8",
)

maven_jar(
    name = "jetty_io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VER,
    sha1 = "6d2d7c82a4b5306ec7bf2d6eb4b67460fce77240",
    src_sha1 = "404b560a0d289c904b72d02e2dbb3b9ad73db219",
)

maven_jar(
    name = "jetty_util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VER,
    sha1 = "810c4e4275e399feaf6e8bc51aa72645bdc06205",
    src_sha1 = "b0dea459e5646e9ccf250a236718d720fba7717c",
)
