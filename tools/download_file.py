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

from __future__ import print_function

from hashlib import sha1
from optparse import OptionParser
from os import link, makedirs, path, remove
import shutil
from subprocess import check_call, CalledProcessError
from sys import stderr

REPO_ROOTS = {
  'MAVEN_CENTRAL': 'http://repo1.maven.org/maven2',
}

GERRIT_HOME = path.expanduser('~/.gerritcodereview')
CACHE_DIR = path.join(GERRIT_HOME, 'buck-cache')
LOCAL_PROPERTIES = 'local.properties'


def hashfile(p):
  d = sha1()
  with open(p, 'rb') as f:
    while True:
      b = f.read(8192)
      if not b:
        break
      d.update(b)
  return d.hexdigest()

def safe_mkdirs(d):
  if path.isdir(d):
    return
  try:
    makedirs(d)
  except OSError as err:
    if not path.isdir(d):
      raise err

def download_properties(root_dir):
  """ Get the download properties.

  First tries to find the properties file in the given root directory,
  and if not found there, tries in the Gerrit settings folder in the
  user's home directory.

  Returns a set of download properties, which may be empty.

  """
  p = {}
  local_prop = path.join(root_dir, LOCAL_PROPERTIES)
  if not path.isfile(local_prop):
    local_prop = path.join(GERRIT_HOME, LOCAL_PROPERTIES)
  if path.isfile(local_prop):
    try:
      with open(local_prop) as fd:
        for line in fd:
          if line.startswith('download.'):
            d = [e.strip() for e in line.split('=', 1)]
            name, url = d[0], d[1]
            p[name[len('download.'):]] = url
    except OSError:
      pass
  return p

def cache_entry(args):
  if args.v:
    h = args.v
  else:
    h = sha1(args.u).hexdigest()
  name = '%s-%s' % (path.basename(args.o), h)
  return path.join(CACHE_DIR, name)

def resolve_url(url, redirects):
  s = url.find(':')
  if s < 0:
    return url
  scheme, rest = url[:s], url[s+1:]
  if scheme not in REPO_ROOTS:
    return url
  if scheme in redirects:
    root = redirects[scheme]
  else:
    root = REPO_ROOTS[scheme]
  root = root.rstrip('/')
  rest = rest.lstrip('/')
  return '/'.join([root, rest])

opts = OptionParser()
opts.add_option('-o', help='local output file')
opts.add_option('-u', help='URL to download')
opts.add_option('-v', help='expected content SHA-1')
args, _ = opts.parse_args()

root_dir = args.o
while root_dir:
  root_dir, n = path.split(root_dir)
  if n == 'buck-out':
    break

redirects = download_properties(root_dir)
cache_ent = cache_entry(args)
src_url = resolve_url(args.u, redirects)

if not path.exists(cache_ent):
  try:
    safe_mkdirs(path.dirname(cache_ent))
  except OSError as err:
    print('error creating directory %s: %s' %
          (path.dirname(cache_ent), err), file=stderr)
    exit(1)

  print('Download %s' % src_url, file=stderr)
  try:
    check_call(['curl', '--proxy-anyauth', '-sfo', cache_ent, src_url])
  except OSError as err:
    print('could not invoke curl: %s\nis curl installed?' % err, file=stderr)
    exit(1)
  except CalledProcessError as err:
    print('error using curl: %s' % err, file=stderr)
    exit(1)

if args.v:
  have = hashfile(cache_ent)
  if args.v != have:
    print((
      '%s:\n' +
      'expected %s\n' +
      'received %s\n') % (src_url, args.v, have), file=stderr)
    try:
      remove(cache_ent)
    except OSError as err:
      if path.exists(cache_ent):
        print('error removing %s: %s' % (cache_ent, err), file=stderr)
    exit(1)

safe_mkdirs(path.dirname(args.o))
try:
  link(cache_ent, args.o)
except OSError as err:
  try:
    shutil.copyfile(cache_ent, args.o)
  except (shutil.Error, IOError) as err:
    print("error copying %s: %s" % (args.o, err), file=stderr)
    exit(1)
