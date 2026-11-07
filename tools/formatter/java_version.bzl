# Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Distribution License v. 1.0 which is available at
# https://www.eclipse.org/org/documents/edl-v10.php.
#
# SPDX-License-Identifier: BSD-3-Clause

"""Expose Bazel's selected Java source level to the JDT formatter."""

def _formatter_config_impl(ctx):
    version = ctx.toolchains["@bazel_tools//tools/jdk:toolchain_type"].java.source_version
    output = ctx.actions.declare_file("FormatterConfig.java")
    ctx.actions.write(output, """package org.eclipse.jgit.format;
final class FormatterConfig {
    static final String JAVA_VERSION = "%s";
    private FormatterConfig() {}
}
""" % version)
    return [DefaultInfo(files = depset([output]))]

formatter_config = rule(
    implementation = _formatter_config_impl,
    toolchains = ["@bazel_tools//tools/jdk:toolchain_type"],
)
