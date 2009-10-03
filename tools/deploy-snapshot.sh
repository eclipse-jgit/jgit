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



if ! test -d .git -o -f .git
then
  echo >&2 'fatal: This script only works in a git repository'
  exit 1
fi

if test -n "$(git ls-files --others --exclude-standard)"
then
  echo >&2 'fatal: untracked files present; cleanup and run again'
  exit 1
fi

git update-index -q --refresh
if test -n "$(git diff-index --name-only HEAD --)"
then
  echo >&2 'fatal: dirty source tree; cleanup and run again'
  exit 1
fi

if V=$(git describe --abbrev=8 HEAD 2>/dev/null)
then
  V=$(echo "$V" | sed -e s/^v//)
else
  n=$(git rev-list HEAD | wc -l)
  r=$(git rev-list -1 --abbrev=8 --abbrev-commit HEAD)
  V=0.5.9-$n-g$r
fi

echo >&2 "Building $V"
for pom in $(git ls-files | grep pom.xml)
do
  perl -i -e "
    while (<>) {
      if (/<version>/) {
        s|<version>.*</version>|<version>$V</version>|;
        print;
        last;
      }
      print;
    }
    print while (<>);
	" "$pom" || exit 1
done

mvn -P jgit-release clean verify package &&
mvn -P jgit-release -Dmaven.test.skip=true deploy
git reset --hard
