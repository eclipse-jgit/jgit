package(default_visibility = ["//visibility:public"])

java_library(
    name = "jgit-archive",
    srcs = glob(
        ["src/**/*.java"],
        exclude = ["src/org/eclipse/jgit/archive/FormatActivator.java"],
    ),
    resource_strip_prefix = "org.eclipse.jgit.archive/resources",
    resources = glob(["resources/**"]),
    deps = [
        "//lib:commons-compress",
        # We want these deps to be provided_deps
        "//org.eclipse.jgit:jgit",
    ],
)
