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

#include <dirent.h>
#include <stdlib.h>
#include <stddef.h>
#include <unistd.h>

static struct {
	jclass _class;

	jfieldID name;
	jfieldID type;
} DirEnt;

int jgit_list_OnLoad(JNIEnv *env) {
	JGIT_CLASS( DirEnt, "org/eclipse/jgit/util/fs/DirEnt" );
	JGIT_FIELD( DirEnt, String, name );
	JGIT_FIELD( DirEnt, int,    type );
	return 0;
}

void jgit_list_OnUnload(JNIEnv *env) {
	JGIT_DELETE_CLASS( DirEnt );
}

static jint dtype(struct dirent *e) {
	switch (e->d_type) {
	case DT_DIR:     return 1;
	case DT_REG:     return 2;
	case DT_LNK:     return 3;

	default:         
	case DT_UNKNOWN: return 0;
	}
}

static jobjectArray resize_array(JNIEnv *env,
		jobjectArray src, jint new_cap, jint live_cnt) {
	jobjectArray dst;
	jint i;
	
	dst = (*env)->NewObjectArray(env, new_cap, DirEnt._class, NULL);
	if (!dst)
		return NULL;

	for (i = 0; i < live_cnt; i++) {
		jobject e = (*env)->GetObjectArrayElement(env, src, i);
		(*env)->SetObjectArrayElement(env, dst, i, e);
	}
	(*env)->DeleteLocalRef(env, src);
	return dst;
}

JNIEXPORT jobjectArray JNICALL
Java_org_eclipse_jgit_util_fs_FileAccessNative_listNative(
		JNIEnv *env, jclass clazz, jstring path) {
	char *path_str = NULL;
	DIR *dirp = NULL;
	struct dirent *entryp = NULL;
	jobjectArray res = NULL;
	jint res_cap = 16;
	jint res_cnt = 0;

	path_str = jgit_GetStringNative(env, path);
	if (!path_str)
		goto fail;

	entryp = malloc(offsetof(struct dirent, d_name)
		+ pathconf(path_str, _PC_NAME_MAX) + 1);
	if (!entryp)
		goto fail;

	res = (*env)->NewObjectArray(env, res_cap, DirEnt._class, NULL);
	if (!res)
		goto fail;

	dirp = opendir(path_str);
	if (!dirp)
		goto fail;

	for (;;) {
		struct dirent *e;
		int s;
		jstring name;
		jobject d;
		
		s = readdir_r(dirp, entryp, &e);
		if (s)
			goto fail;
		if (!e)
			break;

		if (e->d_name[0] == '.') {
			if (!e->d_name[1])
				continue;
			if (e->d_name[1] == '.' && !e->d_name[2])
				continue;
		}

		if (res_cnt == res_cap) {
			res = resize_array(env, res, 2 * res_cap, res_cnt);
			if (!res)
				goto fail;
			res_cap = 2 * res_cap;
		}

		name = jgit_NewNativeString(env, e->d_name, strlen(e->d_name));
		if (!name)
			goto fail;

		d = JGIT_ALLOC( DirEnt );
		if (!d)
			goto fail;

		JGIT_SET( d, DirEnt, String, name, name );
		JGIT_SET( d, DirEnt, int,    type, dtype(e) );

		(*env)->SetObjectArrayElement(env, res, res_cnt++, d );
		(*env)->DeleteLocalRef(env, d);
		(*env)->DeleteLocalRef(env, name);
	}

	closedir(dirp);
	free(path_str);
	free(entryp);

	if (res_cnt != res_cap)
		res = resize_array(env, res, res_cnt, res_cnt);
	return res;

fail:
	jgit_ThrowErrno(env, path_str);
	if (dirp)
		closedir(dirp);
	if (path_str)
		free(path_str);
	if (entryp)
		free(entryp);
	return NULL;
}
