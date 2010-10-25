/*
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
 *
 * lstat.c
 *
 * Providing access to lstat() data from java via JNI
 */
#define _POSIX_C_SOURCE 1

#include <errno.h>
#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>

#include "org_eclipse_jgit_util_fs_os_NativeFSAccess.h"

/*
 * Class:     org_eclipse_jgit_util_fs_os_NativeFSAccess
 * Method:    lstatImpl
 * Signature: (Ljava/lang/String;)[I
 */
JNIEXPORT jintArray JNICALL Java_org_eclipse_jgit_util_fs_os_NativeFSAccess_lstatImpl(
		JNIEnv *env, jclass clazz, jstring path) {
	struct stat finfo;
	jboolean iscopy;

	const char *mfile = (*env)->GetStringUTFChars(env, path, &iscopy);

	jintArray ji_res = (*env)->NewIntArray(env, 10);
	if (lstat(mfile, &finfo) == 0) {
		jint *elems = (*env)->GetIntArrayElements(env, ji_res, 0);
		elems[0] = (jint) finfo.st_mtime;
		elems[1] = (jint) finfo.st_mtimensec;
		elems[2] = (jint) finfo.st_ctime;
		elems[3] = (jint) finfo.st_ctimensec;
		elems[4] = (jint) finfo.st_dev;
		elems[5] = (jint) finfo.st_ino;
		elems[6] = (jint) finfo.st_mode;
		elems[7] = (jint) finfo.st_uid;
		elems[8] = (jint) finfo.st_gid;
		elems[9] = (jint) finfo.st_size;

		printf("direct data from lstat()\n:");
		printf("mtime: %li\n", finfo.st_mtime);
		printf("mtimensec: %li\n", finfo.st_mtimensec);
		printf("ctime: %li\n", finfo.st_ctime);
		printf("ctimensec: %li\n", finfo.st_ctimensec);
		printf("dev: %u\n", finfo.st_dev);
		printf("ino: %llu\n", finfo.st_ino);
		printf("mode: %u\n", finfo.st_mode);
		printf("uid: %u\n", finfo.st_uid);
		printf("gid: %u\n", finfo.st_gid);
		printf("size: %llu\n", finfo.st_size);

		(*env)->ReleaseIntArrayElements(env, ji_res, elems, 0);
	} else {
		// something went wrong, throw a Java exception to inform the caller
		jclass newExcCls;
		newExcCls = (*env)->FindClass(env,
				"org/eclipse/jgit/util/fs/LStatException");
		if (newExcCls == NULL) {
			/* Unable to find the exception class, give up. */
			return NULL;
		}
		(*env)->ThrowNew(env, newExcCls, strerror(errno));
	}
	(*env)->ReleaseStringUTFChars(env, path, mfile);

	return ji_res;
}
