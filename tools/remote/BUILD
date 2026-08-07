# RBE execution platform. Names the container Bazel actions run in on the
# remote executor. JGit is pure Java and schedules no C++ actions, so the
# default (autodetected) cc toolchain satisfies analysis-time resolution
# and is never invoked; only this platform, naming the container image,
# is needed -- there is no generated CROSSTOOL to maintain.
platform(
    name = "platform",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
    ],
    exec_properties = {
        "OSFamily": "Linux",
        "container-image": "docker://gcr.io/bazel-public/ubuntu2404@sha256:57bbaa84bec679736c53dcd1d326e8f835b3b9ce3e36c12a3c12a07eb59177d3",
    },
    visibility = ["//visibility:public"],
)
