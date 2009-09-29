#!/bin/sh
# Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
# Copyright (C) 2008-2009, Google Inc.
# Copyright (C) 2009, Johannes Schindelin <Johannes.Schindelin@gmx.de>
# Copyright (C) 2008, Mike Ralphson <mike@abacus.co.uk>
# Copyright (C) 2009, Nicholas Campbell <nicholas.j.campbell@gmail.com>
# Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@gmail.com>
# Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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


O_CLI=jgit
O_JAR=jgit.jar
O_SRC=jgit_src.zip
O_DOC=jgit_docs.zip

PLUGINS="
	org.eclipse.jgit
	org.eclipse.jgit.pgm
"
JARS="
	org.eclipse.jgit/lib/jsch-0.1.37.jar
	org.eclipse.jgit.pgm/lib/args4j-2.0.9.jar
"

PSEP=":"
T=".temp$$.$O_CLI"
T_MF="$T.MF"
R=`pwd`
if [ "$OSTYPE" = "cygwin" ]
then
	R=`cygpath -m $R`
	PSEP=";"
fi
if [ "$MSYSTEM" = "MINGW" -o "$MSYSTEM" = "MINGW32" ]
then
	PSEP=";"
	R=`pwd -W`
fi

if [ -n "$JAVA_HOME" ]
then
	PATH=${JAVA_HOME}/bin${PSEP}${PATH}
fi

cleanup_bin() {
	rm -f $T $O_CLI+ $O_JAR+ $O_SRC+ $T_MF
	for p in $PLUGINS
	do
		rm -rf $p/bin2
	done
	rm -rf docs
}

die() {
	cleanup_bin
	rm -f $O_CLI $O_JAR $O_SRC
	echo >&2 "$@"
	exit 1
}

cleanup_bin
rm -f $O_CLI $O_JAR $O_SRC $O_DOC

VN=`git describe --abbrev=4 HEAD 2>/dev/null`
git update-index -q --refresh
if [ -n "`git diff-index --name-only HEAD --`" ]
then
	VN="$VN-dirty"
fi
VN=${VN:-untagged}`echo "$VN" | sed -e s/-/./g`

CLASSPATH=
for j in $JARS
do
	if [ -z "$CLASSPATH" ]
	then
		CLASSPATH="$R/$j"
	else
		CLASSPATH="${CLASSPATH}${PSEP}$R/$j"
	fi
done
export CLASSPATH

for p in $PLUGINS
do
	echo "Entering $p ..."
	(cd $p/src &&
	 mkdir ../bin2 &&
	 find . -name \*.java -type f |
	 xargs javac \
		-source 1.5 \
		-target 1.5 \
		-encoding UTF-8 \
		-g \
		-d ../bin2) || die "Building $p failed."
	CLASSPATH="${CLASSPATH}${PSEP}$R/$p/bin2"
done
echo

echo "Version $VN" &&
echo Manifest-Version: 1.0 >$T_MF &&
echo Implementation-Title: jgit >>$T_MF &&
echo Implementation-Version: $VN >>$T_MF &&

java org.eclipse.jgit.pgm.build.JarLinkUtil \
	-include org.eclipse.jgit/bin2 \
	-file META-INF/MANIFEST.MF=$T_MF \
	>$O_JAR+ &&
mv $O_JAR+ $O_JAR &&
echo "Created $O_JAR." &&

java org.eclipse.jgit.pgm.build.JarLinkUtil \
	-include org.eclipse.jgit/src \
	-file META-INF/MANIFEST.MF=$T_MF \
	>$O_SRC+ &&
mv $O_SRC+ $O_SRC &&
echo "Created $O_SRC." &&

M_TB=META-INF/services/org.eclipse.jgit.pgm.TextBuiltin &&
sed s/@@use_self@@/1/ jgit.sh >$O_CLI+ &&
java org.eclipse.jgit.pgm.build.JarLinkUtil \
	`for p in $JARS   ; do printf %s " -include $p"     ;done` \
	`for p in $PLUGINS; do printf %s " -include $p/bin2";done` \
	-file $M_TB=org.eclipse.jgit.pgm/src/$M_TB \
	-file META-INF/MANIFEST.MF=$T_MF \
	>>$O_CLI+ &&
chmod 555 $O_CLI+ &&
mv $O_CLI+ $O_CLI &&
echo "Created $O_CLI." || die "Build failed."

echo "Building Javadocs ..."
for p in $PLUGINS; do
	javadoc -quiet -sourcepath "$p/src/" -d "docs/$p/" \
	`find "$p/src" -name "*.java"`
done

(cd docs && jar cf "../$O_DOC" .)
echo "Created $O_DOC."

cleanup_bin
