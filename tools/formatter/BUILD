# Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Distribution License v. 1.0 which is available at
# https://www.eclipse.org/org/documents/edl-v10.php.
#
# SPDX-License-Identifier: BSD-3-Clause

load("@rules_java//java:defs.bzl", "java_binary", "java_library", "java_test")
load(":java_version.bzl", "formatter_config")

package(default_visibility = ["//visibility:public"])

formatter_config(name = "formatter_config")

java_library(
    name = "formatter_lib",
    srcs = [
        "FormatterMain.java",
        "JdtFormatter.java",
        ":formatter_config",
    ],
    deps = [
        "//lib:args4j",
        "@formatter_deps//:org_eclipse_jdt_org_eclipse_jdt_core",
        "@formatter_deps//:org_eclipse_platform_org_eclipse_text",
    ],
)

java_binary(
    name = "formatter",
    data = ["//tools:eclipse-JGit-Format.xml"],
    main_class = "org.eclipse.jgit.format.FormatterMain",
    runtime_deps = [":formatter_lib"],
)

java_test(
    name = "formatter_test",
    size = "small",
    srcs = ["FormatterTest.java"],
    data = ["//tools:eclipse-JGit-Format.xml"],
    test_class = "org.eclipse.jgit.format.FormatterTest",
    deps = [
        ":formatter_lib",
        "//lib:junit",
    ],
)
