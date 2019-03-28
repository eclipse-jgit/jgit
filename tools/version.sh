#!/bin/sh
# Copyright (C) 2009, Google Inc.
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


# Update all pom.xml with new build number
#
# TODO(spearce) This should be converted to some sort of
# Java based Maven plugin so its fully portable.
#

case "$1" in
--snapshot=*)
	V=$(echo "$1" | perl -pe 's/^--snapshot=//')
	if [ -z "$V" ]
	then
		echo >&2 "usage: $0 --snapshot=0.n.0"
		exit 1
	fi
	case "$V" in
	*-SNAPSHOT) : ;;
	*) V=$V-SNAPSHOT ;;
	esac
	;;

--release)
	V=$(git describe HEAD) || exit
	;;

*)
	echo >&2 "usage: $0 {--snapshot=0.n.0 | --release}"
	exit 1
esac

case "$V" in
v*) V=$(echo "$V" | perl -pe s/^v//) ;;
esac

case "$V" in
*-SNAPSHOT)
	POM_V=$V
	OSGI_V="${V%%-SNAPSHOT}.qualifier"
	;;
*-[1-9]*-g[0-9a-f]*)
	POM_V=$(echo "$V" | perl -pe 's/-(\d+-g.*)$/.$1/')
	OSGI_V=$(perl -e '
		die unless $ARGV[0] =~ /^(\d+)(?:\.(\d+)(?:\.(\d+))?(?:\.\d{12}-r)?)?-(\d+)-g(.*)$/;
		my ($a, $b, $c, $p, $r) = ($1, $2, $3, $4, $5);
		$b = '0' unless defined $b;
		$c = '0' unless defined $c;

		printf "%s.%s.%s.%6.6i_g%s\n", $a, $b, $c, $p, $r;
		' "$V")
	;;
*)
	POM_V=$V
	OSGI_V=$V
	;;
esac

API_V=$(perl -e '
	$ARGV[0] =~ /^(\d+(?:\.\d+(?:\.\d+)?)?)/;
	print $1
	' "$V")

API_N=$(perl -e '
	$ARGV[0] =~ /^(\d+)(?:\.(\d+)(?:\.(\d+))?)?/;
	my ($a, $b) = ($1, $2);
	$b = 0 unless defined $b;
	$b++;
	print "$a.$b.0";
	' "$API_V")

perl -pi~ -e '
	s/^(Bundle-Version:\s*).*$/${1}'"$OSGI_V"'/;
	s/(org.eclipse.jgit.*;version=")[^"[(]*(")/${1}'"$API_V"'${2}/;
	s/(org.eclipse.jgit.*;version="\[)[^"]*(\)")/${1}'"$API_V,$API_N"'${2}/;
	' $(git ls-files | grep META-INF/MANIFEST.MF)

perl -pi~ -e '
	s/^(Bundle-Version:\s*).*$/${1}'"$OSGI_V"'/;
	s/(org.eclipse.jgit.*;version=")[^"[(]*(")/${1}'"$API_V"'${2}/;
	s/(org.eclipse.jgit.*;version="\[)[^"]*(\)")/${1}'"$API_V,$API_N"'${2}/;
	s/^(Eclipse-SourceBundle:\s*org.eclipse.jgit.*;version=\").*(\";roots=\"\.\")$/${1}'"$OSGI_V"'${2}/;
	' $(git ls-files | grep META-INF/SOURCE-MANIFEST.MF)

perl -pi~ -e '
	if ($ARGV ne $old_argv) {
		$seen_version = 0;
		$old_argv = $ARGV;
	}
	if (!$seen_version) {
		$seen_version = 1 if (!/<\?xml/ &&
		s/(version=")[^"]*(")/${1}'"$OSGI_V"'${2}/);
	}
	s/(import feature="org\.eclipse\.jgit[^"]*" version=")[^"]*(")/${1}'"$API_V"'${2}/;
	s/(import plugin="org\.eclipse\.jgit[^"]*" version=")[^"]*(")/${1}'"$API_V"'${2}/;
	' org.eclipse.jgit.packaging/org.*.feature/feature.xml

perl -pi~ -e '
	if ($ARGV ne $old_argv) {
		$seen_version = 0;
		$old_argv = $ARGV;
	}
	if ($seen_version < 2) {
		$seen_version++ if
		s{<(version)>.*</\1>}{<${1}>'"$POM_V"'</${1}>};
	}
	' org.eclipse.jgit.packaging/org.*.source.feature/pom.xml

perl -pi~ -e '
	if ($ARGV ne $old_argv) {
		$seen_version = 0;
		$old_argv = $ARGV;
	}
	if ($seen_version < 18) {
		$seen_version++ if
		s{<(version)>.*</\1>}{<${1}>'"$POM_V"'</${1}>};
	}
	' org.eclipse.jgit.coverage/pom.xml

perl -pi~ -e '
	if ($ARGV ne $old_argv) {
		$seen_version = 0;
		$old_argv = $ARGV;
	}
	if (!$seen_version) {
		$seen_version = 1 if
		s{<(version)>.*</\1>}{<${1}>'"$POM_V"'</${1}>};
	}
	' $(git ls-files | grep pom.xml)

find . -name '*~' | xargs rm -f
git diff
