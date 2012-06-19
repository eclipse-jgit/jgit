#!/bin/bash
# Copyright (C) 2012, Franziska Schmidt <fps2@st-andrews.ac.uk>
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


dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
dir=${dir/#*jgit\/tools/\/jgit\/tools}
if [[ $dir = "/jgit/tools" ]];then 
   echo -e "\nComparing JGit CLI to JGit API... \n"
else
   echo -e "\nPlease place this script in /jgit/tools directory to run it correctly.\n"
   exit 1
fi

api=../org.eclipse.jgit/src/org/eclipse/jgit/api
cli=../org.eclipse.jgit.pgm/src/org/eclipse/jgit/pgm

find $api -type f | while read file; do 
   check=${file/%Command.java/.java} #replace end
   check=${check/#*\//} #replace beginning before slash
   [[ -f $cli/${check} ]] && echo ">> Implemented in cli:"  $file "as" "$check"
   [[ ! -f $cli/${check} ]] && echo "Additional file in api (not in cli yet):" $file
done

