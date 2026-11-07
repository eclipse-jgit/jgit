# Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Distribution License v. 1.0 which is available at
# https://www.eclipse.org/org/documents/edl-v10.php.
#
# SPDX-License-Identifier: BSD-3-Clause

load("@rules_java//java:defs.bzl", "java_binary", "java_library", "java_test")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "formatter_lib",
    srcs = [
        "FormatterMain.java",
        "GitDiff.java",
        "JdtFormatter.java",
    ],
    deps = [
        "//org.eclipse.jgit:jgit",
        "@formatter_deps//:org_eclipse_jdt_org_eclipse_jdt_core",
        "@formatter_deps//:org_eclipse_platform_org_eclipse_text",
    ],
)

java_binary(
    name = "formatter",
    main_class = "org.eclipse.jgit.format.FormatterMain",
    runtime_deps = [":formatter_lib"],
    data = ["//tools:eclipse-JGit-Format.xml"],
)

java_test(
    name = "formatter_test",
    size = "small",
    srcs = ["FormatterTest.java"],
    test_class = "org.eclipse.jgit.format.FormatterTest",
    data = ["//tools:eclipse-JGit-Format.xml"],
    deps = [
        "//org.eclipse.jgit:jgit",
        ":formatter_lib",
        "@external_deps//:junit_junit",
    ],
)
