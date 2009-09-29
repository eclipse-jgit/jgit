#!/bin/sh
# Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com>
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


# Updates MANIFEST.MF files for EGit plugins.

v=$1
if [ -z "$v" ]
then
	echo >&2 "usage: $0 version"
	exit 1
fi

MF=$(git ls-files | grep META-INF/MANIFEST.MF)
MV=jgit-maven/jgit/pom.xml
ALL="$MF $MV"

replace() {
	version=$1

	perl -pi -e 's/^(Bundle-Version:).*/$1 '$version/ $MF
	perl -pi -e 's,^    <version>.*</version>,    <version>'$2'</version>,' $MV
}

replace $v $v
git commit -s -m "JGit $v" $ALL &&
c=$(git rev-parse HEAD) &&

replace $v.qualifier $v-SNAPSHOT &&
git commit -s -m "Re-add version qualifier suffix to $v" $ALL &&

echo &&
tagcmd="git tag -s -m 'JGit $v' v$v $c" &&
if ! eval $tagcmd
then
	echo >&2
	echo >&2 "Tag with:"
	echo >&2 "  $tagcmd"
	exit 1
fi || exit
