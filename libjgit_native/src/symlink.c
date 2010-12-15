/*
 * Copyright (C) 2010, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include "libjgit.h"
#include "org_eclipse_jgit_util_fs_FileAccessNative.h"

#include <unistd.h>
#include <stdlib.h>

JNIEXPORT jstring JNICALL
Java_org_eclipse_jgit_util_fs_FileAccessNative_readlinkImp(
		JNIEnv *env, jclass clazz, jstring path) {
	jstring r = NULL;
	char *path_str;
	char small_buf[128];
	size_t buf_sz = sizeof(small_buf);
	char *buf_ptr = small_buf;
	ssize_t n;

	path_str = jgit_GetStringNative(env, path);
	if (!path_str)
		return NULL;

try_read:
	n = readlink(path_str, buf_ptr, buf_sz);
	if (n == buf_sz) {
		size_t d2 = buf_sz + buf_sz;
		if (d2 < buf_sz) {
			jgit_ThrowOutOfMemory(env);
			goto done;
		}

		if (buf_ptr != small_buf) {
			free(buf_ptr);
			buf_ptr = NULL;
		}

		buf_ptr = malloc(d2);
		if (!buf_ptr) {
			jgit_ThrowOutOfMemory(env);
			goto done;
		}

		buf_sz = d2;
		goto try_read;
	}

	if (n < 0) {
		jgit_ThrowErrno(env, path_str);
		goto done;
	}

	r = jgit_NewNativeString(env, buf_ptr, n);

done:
	if (buf_ptr && buf_ptr != small_buf)
		free(buf_ptr);
	free(path_str);
	return r;
}

JNIEXPORT void JNICALL
Java_org_eclipse_jgit_util_fs_FileAccessNative_symlinkImp(
		JNIEnv *env, jclass clazz, jstring path, jstring target) {
	char *path_str;
	char *target_str;

	path_str = jgit_GetStringNative(env, path);
	if (!path_str)
		return;

	target_str = jgit_GetStringNative(env, target);
	if (!target_str) {
		free(path_str);
		return;
	}

	if (symlink(target_str, path_str))
		jgit_ThrowErrno(env, path_str);

	free(target_str);
	free(path_str);
}
