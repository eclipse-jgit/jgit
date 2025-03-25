load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "args4j",
    visibility = [
        "//org.eclipse.jgit.pgm:__pkg__",
        "//org.eclipse.jgit.pgm.test:__pkg__",
    ],
    exports = ["@jgit_deps//:args4j_args4j"],
)

java_library(
    name = "commons-compress",
    visibility = [
        "//org.eclipse.jgit.archive:__pkg__",
        "//org.eclipse.jgit.pgm.test:__pkg__",
        "//org.eclipse.jgit.test:__pkg__",
    ],
    exports = ["@jgit_deps//:org_apache_commons_commons_compress"],
)

java_library(
    name = "commons-lang3",
    visibility = [
        "//org.eclipse.jgit.archive:__pkg__",
        "//org.eclipse.jgit.pgm.test:__pkg__",
        "//org.eclipse.jgit.test:__pkg__",
    ],
    exports = ["@jgit_deps//:org_apache_commons_commons_lang3"],
)

java_library(
    name = "commons-io",
    visibility = [
        "//org.eclipse.jgit.archive:__pkg__",
        "//org.eclipse.jgit.pgm.test:__pkg__",
        "//org.eclipse.jgit.test:__pkg__",
    ],
    exports = ["@jgit_deps//:commons_io_commons_io"],
)

java_library(
    name = "commons-codec",
    visibility = [
        "//org.eclipse.jgit:__pkg__",
        "//org.eclipse.jgit.test:__pkg__",
    ],
    exports = ["@jgit_deps//:commons_codec_commons_codec"],
)

java_library(
    name = "commons-logging",
    visibility = ["//visibility:public"],
    exports = ["@jgit_deps//:commons_logging_commons_logging"],
)

java_library(
    name = "gson",
    visibility = [
        "//org.eclipse.jgit.lfs:__pkg__",
        "//org.eclipse.jgit.lfs.server:__pkg__",
    ],
    exports = ["@jgit_deps//:com_google_code_gson_gson"],
)

java_library(
    name = "httpclient",
    visibility = [
        "//org.eclipse.jgit.http.apache:__pkg__",
        "//org.eclipse.jgit.lfs.server.test:__pkg__",
        "//org.eclipse.jgit.pgm:__pkg__",
    ],
    exports = ["@jgit_deps//:org_apache_httpcomponents_httpclient"],
)

java_library(
    name = "httpcore",
    visibility = [
        "//org.eclipse.jgit.http.apache:__pkg__",
        "//org.eclipse.jgit.http.test:__pkg__",
        "//org.eclipse.jgit.lfs.server:__pkg__",
        "//org.eclipse.jgit.lfs.server.test:__pkg__",
        "//org.eclipse.jgit.pgm:__pkg__",
    ],
    exports = ["@jgit_deps//:org_apache_httpcomponents_httpcore"],
)

java_library(
    name = "sshd-osgi",
    visibility = [
        "//org.eclipse.jgit.junit.ssh:__pkg__",
        "//org.eclipse.jgit.ssh.apache:__pkg__",
        "//org.eclipse.jgit.ssh.apache.agent:__pkg__",
        "//org.eclipse.jgit.ssh.apache.test:__pkg__",
        "//org.eclipse.jgit.test:__pkg__",
    ],
    exports = ["@jgit_deps//:org_apache_sshd_sshd_osgi"],
)

java_library(
    name = "sshd-sftp",
    visibility = [
        "//org.eclipse.jgit.junit.ssh:__pkg__",
        "//org.eclipse.jgit.ssh.apache:__pkg__",
        "//org.eclipse.jgit.ssh.apache.test:__pkg__",
        "//org.eclipse.jgit.test:__pkg__",
    ],
    exports = ["@jgit_deps//:org_apache_sshd_sshd_sftp"],
)

java_library(
    name = "jna",
    visibility = [
        "//org.eclipse.jgit.ssh.apache.agent:__pkg__",
    ],
    exports = ["@jgit_deps//:net_java_dev_jna_jna"],
)

java_library(
    name = "jna-platform",
    visibility = [
        "//org.eclipse.jgit.ssh.apache.agent:__pkg__",
    ],
    exports = ["@jgit_deps//:net_java_dev_jna_jna_platform"],
)

java_library(
    name = "javaewah",
    visibility = ["//visibility:public"],
    exports = ["@jgit_deps//:com_googlecode_javaewah_JavaEWAH"],
)

java_library(
    name = "jetty-http",
    # TODO: This should be testonly but org.eclipse.jgit.pgm depends on it.
    visibility = ["//visibility:public"],
    exports = ["@jgit_deps//:org_eclipse_jetty_jetty_http"],
    runtime_deps = [":commons-codec"],
)

java_library(
    name = "jetty-io",
    # TODO: This should be testonly but org.eclipse.jgit.pgm depends on it.
    visibility = ["//visibility:public"],
    exports = ["@jgit_deps//:org_eclipse_jetty_jetty_io"],
)

java_library(
    name = "jetty-security",
    # TODO: This should be testonly but org.eclipse.jgit.pgm depends on it.
    visibility = ["//visibility:public"],
    exports = ["@jgit_deps//:org_eclipse_jetty_jetty_security"],
)

java_library(
    name = "jetty-session",
    # TODO: This should be testonly but org.eclipse.jgit.pgm depends on it.
    visibility = ["//visibility:public"],
    exports = ["@jetty-session//jar"],
)

java_library(
    name = "jetty-server",
    # TODO: This should be testonly but org.eclipse.jgit.pgm depends on it.
    visibility = ["//visibility:public"],
    exports = ["@jgit_deps//:org_eclipse_jetty_jetty_server"],
)

java_library(
    name = "jetty-servlet",
    # TODO: This should be testonly but org.eclipse.jgit.pgm depends on it.
    visibility = ["//visibility:public"],
    exports = [
        "@jgit_deps//:org_eclipse_jetty_jetty_servlet",
        "@jgit_deps//:org_eclipse_jetty_jetty_util_ajax",
    ],
)

java_library(
    name = "jetty-util",
    # TODO: This should be testonly but org.eclipse.jgit.pgm depends on it.
    visibility = ["//visibility:public"],
    exports = ["@jgit_deps//:org_eclipse_jetty_jetty_util"],
)

java_library(
    name = "jetty-ee8-nested",
    # TODO: This should be testonly but org.eclipse.jgit.pgm depends on it.
    visibility = ["//visibility:public"],
    exports = ["@jetty-nested//jar"],
)

java_library(
    name = "jetty-ee8-security",
    # TODO: This should be testonly but org.eclipse.jgit.pgm depends on it.
    visibility = ["//visibility:public"],
    exports = ["@jetty-ee8-security//jar"],
)


java_library(
    name = "jsch",
    visibility = [
        "//org.eclipse.jgit.junit.ssh:__pkg__",
        "//org.eclipse.jgit.ssh.jsch:__pkg__",
        "//org.eclipse.jgit.ssh.jsch.test:__pkg__",
    ],
    exports = ["@jgit_deps//:com_jcraft_jsch"],
)

java_library(
    name = "bcpg",
    visibility = [
        "//org.eclipse.jgit.gpg.bc:__pkg__",
        "//org.eclipse.jgit.gpg.bc.test:__pkg__",
        "//org.eclipse.jgit.ssh.apache:__pkg__",
        "//org.eclipse.jgit.ssh.apache.test:__pkg__",
        "//org.eclipse.jgit.test:__pkg__",
    ],
    exports = ["@jgit_deps//:org_bouncycastle_bcpg_jdk18on"],
)

java_library(
    name = "bcprov",
    visibility = [
        "//org.eclipse.jgit.gpg.bc:__pkg__",
        "//org.eclipse.jgit.gpg.bc.test:__pkg__",
        "//org.eclipse.jgit.ssh.apache:__pkg__",
        "//org.eclipse.jgit.ssh.apache.test:__pkg__",
        "//org.eclipse.jgit.ssh.jsch.test:__pkg__",
        "//org.eclipse.jgit.test:__pkg__",
    ],
    exports = ["@jgit_deps//:org_bouncycastle_bcprov_jdk18on"],
)

java_library(
    name = "bcutil",
    visibility = [
        "//org.eclipse.jgit.gpg.bc:__pkg__",
        "//org.eclipse.jgit.gpg.bc.test:__pkg__",
        "//org.eclipse.jgit.ssh.apache:__pkg__",
        "//org.eclipse.jgit.ssh.apache.test:__pkg__",
        "//org.eclipse.jgit.ssh.jsch.test:__pkg__",
        "//org.eclipse.jgit.test:__pkg__",
    ],
    exports = ["@jgit_deps//:org_bouncycastle_bcutil_jdk18on"],
)

java_library(
    name = "bcpkix",
    visibility = [
        "//org.eclipse.jgit.gpg.bc:__pkg__",
        "//org.eclipse.jgit.ssh.apache:__pkg__",
        "//org.eclipse.jgit.ssh.apache.test:__pkg__",
        "//org.eclipse.jgit.ssh.jsch.test:__pkg__",
        "//org.eclipse.jgit.test:__pkg__",
    ],
    exports = ["@jgit_deps//:org_bouncycastle_bcpkix_jdk18on"],
)

java_library(
    name = "jzlib",
    visibility = [
        "//org.eclipse.jgit.ssh.jsch:__pkg__",
        "//org.eclipse.jgit.test:__pkg__",
    ],
    exports = ["@jgit_deps//:com_jcraft_jzlib"],
)

java_library(
    name = "junit",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = [
        "@jgit_deps//:net_bytebuddy_byte_buddy_agent",
        "@jgit_deps//:net_bytebuddy_byte_buddy",
        "@jgit_deps//:org_hamcrest_hamcrest",
        "@jgit_deps//:junit_junit",
        "@jgit_deps//:org_mockito_mockito_core",
        "@jgit_deps//:org_objenesis_objenesis",
    ],
)

java_library(
    name = "mockito",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = [
        "@jgit_deps//:net_bytebuddy_byte_buddy_agent",
        "@jgit_deps//:net_bytebuddy_byte_buddy",
        "@jgit_deps//:org_mockito_mockito_core",
        "@jgit_deps//:org_objenesis_objenesis",
    ],
)

java_library(
    name = "assertj-core",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = [
        "@jgit_deps//:org_assertj_assertj_core",
    ],
)

java_library(
    name = "servlet-api",
    visibility = [
        "//org.eclipse.jgit.http.apache:__pkg__",
        "//org.eclipse.jgit.http.server:__pkg__",
        "//org.eclipse.jgit.http.test:__pkg__",
        "//org.eclipse.jgit.junit.http:__pkg__",
        "//org.eclipse.jgit.lfs.server:__pkg__",
        "//org.eclipse.jgit.lfs.server.test:__pkg__",
        "//org.eclipse.jgit.pgm:__pkg__",
    ],
    exports = ["@jgit_deps//:jakarta_servlet_jakarta_servlet_api_4_0_4"],
)

java_library(
    name = "slf4j-api",
    visibility = ["//visibility:public"],
    exports = ["@jgit_deps//:org_slf4j_slf4j_api"],
)

java_library(
    name = "slf4j-simple",
    visibility = ["//visibility:public"],
    exports = ["@jgit_deps//:org_slf4j_slf4j_simple"],
)

java_library(
    name = "xz",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = ["@jgit_deps//:org_tukaani_xz"],
)
