# Copyright (C) 2019, Matthias Sohn <matthias.sohn@sap.com>
# and other copyright owners as documented in the project's IP log.
#
# This program and the accompanying materials are made available
# under the terms of the Eclipse Distribution License v1.0 which
# accompanies this distribution, is reproduced below, and is
# available at http://www.eclipse.org/org/documents/edl-v10.php
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or
# without modification, are permitted provided that the following
# conditions are met:
#
# - Redistributions of source code must retain the above copyright
#   notice, this list of conditions and the following disclaimer.
#
# - Redistributions in binary form must reproduce the above
#   copyright notice, this list of conditions and the following
#   disclaimer in the documentation and/or other materials provided
#   with the distribution.
#
# - Neither the name of the Eclipse Foundation, Inc. nor the
#   names of its contributors may be used to endorse or promote
#   products derived from this software without specific prior
#   written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
# CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
# INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
# OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
# CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
# NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
# LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
# STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
# ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
# ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

# Definitions to run jmh microbenchmarks

load("@rules_java//java:defs.bzl", "java_binary", "java_plugin")

def jmh_java_benchmarks(name, srcs, deps = [], tags = [], plugins = [], **kwargs):
    """Builds runnable JMH benchmarks.
    This rule builds a runnable target for one or more JMH benchmarks
    specified as srcs. It takes the same arguments as java_binary,
    except for main_class.
    """
    plugin_name = "_{}_jmh_annotation_processor".format(name)
    java_plugin(
        name = plugin_name,
        deps = ["//lib/jmh:jmh"],
        processor_class = "org.openjdk.jmh.generators.BenchmarkProcessor",
        visibility = ["//visibility:private"],
        tags = tags,
    )
    java_binary(
        name = name,
        srcs = srcs,
        main_class = "org.openjdk.jmh.Main",
        deps = deps + ["//lib/jmh:jmh"],
        plugins = plugins + [plugin_name],
        tags = tags,
        **kwargs
    )
