package(default_visibility = ["//visibility:public"])

SRCS = glob(["src/**/*.java"])

RESOURCES = glob(["resources/**"])

java_library(
    name = "ssh-apache",
    srcs = SRCS,
    javacopts = select({
        "//:jdk9": ["--add-modules=java.xml.bind"],
        "//conditions:default": [],
    }),
    resource_strip_prefix = "org.eclipse.jgit.ssh.apache/resources",
    resources = RESOURCES,
    deps = [
        "//org.eclipse.jgit:jgit",
        "//lib:sshd-core",
        "//lib:sshd-sftp",
        "//lib:slf4j-api",
    ],
)
