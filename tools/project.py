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
#
# TODO(sop): Remove hack after Buck supports Eclipse

from __future__ import print_function
from optparse import OptionParser
from os import path
from subprocess import Popen, PIPE, CalledProcessError, check_call
from xml.dom import minidom
import re
import sys

SRC = ['//tools:eclipse_classpath']
JRE = '/'.join([
  'org.eclipse.jdt.launching.JRE_CONTAINER',
  'org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType',
  'JavaSE-1.7',
])

ROOT = path.abspath(__file__)
for _ in range(0, 2):
  ROOT = path.dirname(ROOT)

opts = OptionParser()
opts.add_option('--src', action='store_true')
args, _ = opts.parse_args()

def gen_project():
  p = path.join(ROOT, '.project')
  with open(p, 'w') as fd:
    print("""\
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
  <name>jgit</name>
  <buildSpec>
    <buildCommand>
      <name>org.eclipse.jdt.core.javabuilder</name>
    </buildCommand>
  </buildSpec>
  <natures>
    <nature>org.eclipse.jdt.core.javanature</nature>
  </natures>
</projectDescription>\
""", file=fd)

def gen_classpath():
  def query_classpath(targets):
    deps = []
    p = Popen(['buck', 'audit', 'classpath'] + targets, stdout=PIPE)
    for line in p.stdout:
      deps.append(line.strip())
    s = p.wait()
    if s != 0:
      exit(s)
    return deps

  def make_classpath():
    impl = minidom.getDOMImplementation()
    return impl.createDocument(None, 'classpath', None)

  def classpathentry(kind, path, src=None):
    e = doc.createElement('classpathentry')
    e.setAttribute('kind', kind)
    e.setAttribute('path', path)
    if src:
      e.setAttribute('sourcepath', src)
    doc.documentElement.appendChild(e)

  doc = make_classpath()
  src = set()
  lib = set()

  java_library = re.compile(r'[^/]+/gen/(.*)/lib__[^/]+__output/[^/]+[.]jar$')
  for p in query_classpath(SRC):
    m = java_library.match(p)
    if m:
      src.add(m.group(1))
    else:
      lib.add(p)

  for s in sorted(src):
    for env in ['src', 'resources', 'tst', 'tst-rsrc']:
      p = path.join(s, env)
      if path.exists(p):
        classpathentry('src', p)
        continue

  for j in sorted(lib):
    s = None
    if j.endswith('.jar'):
      s = j[:-4] + '-src.jar'
      if not path.exists(s):
        s = None
    classpathentry('lib', j, s)

  classpathentry('con', JRE)
  classpathentry('output', 'buck-out/classes')

  p = path.join(ROOT, '.classpath')
  with open(p, 'w') as fd:
    doc.writexml(fd, addindent='  ', newl='\n', encoding='UTF-8')

try:
  if args.src:
    try:
      check_call([path.join(ROOT, 'tools', 'download_all.py'), '--src'])
    except CalledProcessError as err:
      exit(1)

  gen_project()
  gen_classpath()

  try:
    check_call(['buck', 'build'] + SRC)
  except CalledProcessError as err:
    exit(1)
except KeyboardInterrupt:
  print('Interrupted by user', file=sys.stderr)
  exit(1)
