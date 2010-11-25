/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2010, Matthias Sohn <matthias.sohn@sap.com>
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

#define _POSIX_C_SOURCE 1
#define TIME_HAS_NS 1

#include "libjgit.h"

#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>

#include "org_eclipse_jgit_util_fs_FSAccessNative.h"

static struct {
	jclass _class;

	jfieldID ctime;
	jfieldID mtime;

	jfieldID ctime_nsec;
	jfieldID mtime_nsec;

	jfieldID dev;
	jfieldID ino;
	jfieldID mode;
	jfieldID uid;
	jfieldID gid;
	jfieldID size;
} LStat;

int jgit_lstat_OnLoad(JNIEnv *env) {
	JGIT_INIT_CLASS(env, LStat, "org/eclipse/jgit/util/fs/LStat");
	JGIT_INIT_FIELD(env, LStat, ctime, "I");
	JGIT_INIT_FIELD(env, LStat, mtime, "I");
	JGIT_INIT_FIELD(env, LStat, ctime_nsec, "I");
	JGIT_INIT_FIELD(env, LStat, mtime_nsec, "I");
	JGIT_INIT_FIELD(env, LStat, dev,  "I");
	JGIT_INIT_FIELD(env, LStat, ino,  "I");
	JGIT_INIT_FIELD(env, LStat, mode, "I");
	JGIT_INIT_FIELD(env, LStat, uid,  "I");
	JGIT_INIT_FIELD(env, LStat, gid,  "I");
	JGIT_INIT_FIELD(env, LStat, size, "J");
	return 0;
}

void jgit_lstat_OnUnload(JNIEnv *env) {
	(*env)->DeleteGlobalRef(env, LStat._class);
}

/*
 * Class:     org_eclipse_jgit_util_fs_FSAccessNative
 * Method:    lstatImpl
 * Signature: (Ljava/lang/String;)Lorg/eclipse/jgit/util/fs/LStat;
 */
JNIEXPORT jobject JNICALL
Java_org_eclipse_jgit_util_fs_FSAccessNative_lstatImpl(
	JNIEnv *env, jclass clazz, jstring path) {
	jobject s = NULL;
	const char *path_str;	
	struct stat st;

	path_str = (*env)->GetStringUTFChars(env, path, NULL);

	if (!lstat(path_str, &st)) {
		s = (*env)->AllocObject(env, LStat._class);
		if (!s)
			goto done;

		(*env)->SetIntField(env, s, LStat.ctime, (jint)st.st_ctime);
		(*env)->SetIntField(env, s, LStat.mtime, (jint)st.st_mtime);

#ifdef TIME_HAS_NS
		(*env)->SetIntField(env, s, LStat.ctime_nsec, (jint)st.st_ctimensec);
		(*env)->SetIntField(env, s, LStat.mtime_nsec, (jint)st.st_mtimensec);
#else
		(*env)->SetIntField(env, s, LStat.ctime_nsec, (jint)0);
		(*env)->SetIntField(env, s, LStat.mtime_nsec, (jint)0);
#endif

		(*env)->SetIntField(env,  s, LStat.dev,  (jint)st.st_dev);
		(*env)->SetIntField(env,  s, LStat.ino,  (jint)st.st_ino);
		(*env)->SetIntField(env,  s, LStat.mode, (jint)st.st_mode);
		(*env)->SetIntField(env,  s, LStat.uid,  (jint)st.st_uid);
		(*env)->SetIntField(env,  s, LStat.gid,  (jint)st.st_gid);
		(*env)->SetLongField(env, s, LStat.size, (jlong)st.st_size);

	} else {
		const char *err;
		const char *msg;
		jclass c;

		switch (errno) {
		case EACCES:
			err = "org/eclipse/jgit/util/fs/AccessDeniedException";
			msg = path_str;
			break;

		case ENOENT:
			err = "org/eclipse/jgit/util/fs/NoSuchFileException";
			msg = path_str;
			break;

		case ENOTDIR:
			err = "org/eclipse/jgit/util/fs/NotDirectoryException";
			msg = path_str;
			break;

		default:
			err = "org/eclipse/jgit/util/fs/LStatException";
			msg = strerror(errno);
			break;
		}

		c = (*env)->FindClass(env, err);
		if (c)
			(*env)->ThrowNew(env, c, msg);
	}

done:
	(*env)->ReleaseStringUTFChars(env, path, path_str);
	return s;
}
