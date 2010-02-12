#!/bin/sh
# Copyright (C) 2010, Matthias Sohn <matthias.sohn@sap.com>
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


TOOLS_DIR=$(dirname $0)
TOOLS_DIR=$(cd $TOOLS_DIR && pwd)
export TOOLS_DIR

MAP_OF_COMMITS=$(pwd)/commit.map
export MAP_OF_COMMITS

: >$MAP_OF_COMMITS
git filter-branch \
--tree-filter '
	export GIT_COMMIT
	$TOOLS_DIR/fix-headers.pl

	if [ -f tools/graft-old-history.sh ]; then
		i=$(map 046198cf5f21e5a63e8ec0ecde2ef3fe21db2eae)
		perl -pi -e "
			s/^POST=.*/POST=$i/;
		" tools/graft-old-history.sh
	fi
' \
--env-filter '
	if [ 046198cf5f21e5a63e8ec0ecde2ef3fe21db2eae = $GIT_COMMIT ]; then
		export GIT_AUTHOR_NAME="Git Development Community"
		export GIT_AUTHOR_EMAIL=git@vger.kernel.org
	fi
' \
--parent-filter '
	if [ 046198cf5f21e5a63e8ec0ecde2ef3fe21db2eae = $GIT_COMMIT ]; then
		cat >/dev/null
	else
		cat
	fi
' \
--commit-filter '
	n=$(git commit-tree "$@")
	echo $GIT_COMMIT=$n >>$MAP_OF_COMMITS
	echo $n
' \
-d /tmp/jgit-rewrite-history-$$ \
$(git for-each-ref --format='%(refname)' refs/heads refs/changes) \
--not 3a2dd9921c8a08740a9e02c421469e5b1a9e47cb
