#!/usr/bin/python
# Copyright 2013, Google Inc.
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

from optparse import OptionParser
import re
from subprocess import check_call, CalledProcessError, Popen, PIPE

MAIN = ['//tools:eclipse_classpath']
PAT = re.compile(r'"(//.*?)" -> "//tools:download_file"')

opts = OptionParser()
opts.add_option('--src', action='store_true')
args, _ = opts.parse_args()

targets = set()

p = Popen(['buck', 'audit', 'classpath', '--dot'] + MAIN, stdout = PIPE)
for line in p.stdout:
  m = PAT.search(line)
  if m:
    n = m.group(1)
    if args.src and n.endswith('__download_bin'):
      n = n[:-4] + '_src'
    targets.add(n)
r = p.wait()
if r != 0:
  exit(r)

try:
  check_call(['buck', 'build'] + sorted(targets))
except CalledProcessError as err:
  exit(1)
