#!/usr/bin/env python
# Copyright (C) 2020, David Ostrovsky <david@ostrovsky.org> and others
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Distribution License v. 1.0 which is available at
# http://www.eclipse.org/org/documents/edl-v10.php.
#
# SPDX-License-Identifier: BSD-3-Clause

# This script will be run by bazel when the build process starts to
# generate key-value information that represents the status of the
# workspace. The output should be like
#
# KEY1 VALUE1
# KEY2 VALUE2
#
# If the script exits with non-zero code, it's considered as a failure
# and the output will be discarded.

from __future__ import print_function
import os
import subprocess
import sys

ROOT = os.path.abspath(__file__)
while not os.path.exists(os.path.join(ROOT, 'WORKSPACE')):
    ROOT = os.path.dirname(ROOT)
CMD = ['git', 'describe', '--always', '--match', 'v[0-9].*', '--dirty']


def revision(directory, parent):
    try:
        os.chdir(directory)
        return subprocess.check_output(CMD).strip().decode("utf-8")
    except OSError as err:
        print('could not invoke git: %s' % err, file=sys.stderr)
        sys.exit(1)
    except subprocess.CalledProcessError as err:
        # ignore "not a git repository error" to report unknown version
        return None
    finally:
        os.chdir(parent)


print("STABLE_BUILD_JGIT_LABEL %s" % revision(ROOT, ROOT))
