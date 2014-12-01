#!/bin/sh
# Copyright (C) 2008-2009, Google Inc.
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

cmd=
for a in "$@"
do
	case "$a" in
	-*) continue ;;
	*)  cmd=$a; break; ;;
	esac
done

use_pager=
case "$cmd" in
blame)    use_pager=1 ;;
diff)     use_pager=1 ;;
log)      use_pager=1 ;;
esac

this_script=`which "$0" 2>/dev/null`
[ $? -gt 0 -a -f "$0" ] && this_script="$0"
cp=$this_script

platform="`uname`"
if [ -n "$JGIT_CLASSPATH" ]
then
	cp_sep=:
	# On Windows & MingW use ";" to separate classpath entries
	[ "${platform#MINGW}" != "$platform" ] && cp_sep=";"
	cp="$cp$cp_sep$JGIT_CLASSPATH"
fi

# Cleanup paths for Cygwin.
#
case "$platform" in
CYGWIN*)
	cp=`cygpath --windows --mixed --path "$cp"`
	;;
Darwin)
	if [ -e /System/Library/Frameworks/JavaVM.framework ]
	then
		java_args='
			-Dcom.apple.mrj.application.apple.menu.about.name=JGit
			-Dcom.apple.mrj.application.growbox.intrudes=false
			-Dapple.laf.useScreenMenuBar=true
			-Xdock:name=JGit
			-Dfile.encoding=UTF-8
		'
	fi
	;;
esac

CLASSPATH="$cp"
export CLASSPATH

java=java
if [ -n "$JAVA_HOME" ]
then
	java="$JAVA_HOME/bin/java"
fi

if [ -n "$use_pager" ]
then
	use_pager=${GIT_PAGER:-${PAGER:-less}}
	[ cat = "$use_pager" ] && use_pager=
fi

if [ -n "$use_pager" ]
then
	LESS=${LESS:-FSRX}
	export LESS

	"$java" $java_args org.eclipse.jgit.pgm.Main "$@" | $use_pager
	exit
else
  exec "$java" $java_args org.eclipse.jgit.pgm.Main "$@"
  exit 1
fi
