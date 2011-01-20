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

#include <errno.h>
#include <string.h>
#include <stdlib.h>
#include "libjgit.h"

static struct { jclass _class; } AccessDenied;
static struct { jclass _class; } NoSuchFile;
static struct { jclass _class; } NotDirectory;
static struct { jclass _class; } NativeException;
static struct { jclass _class; } OutOfMemory;

static struct {
	jclass _class;

	jmethodID getBytes;
} String;

int jgit_util_OnLoad(JNIEnv *env) {
	JGIT_CLASS( AccessDenied, "org/eclipse/jgit/util/fs/AccessDeniedException" );
	JGIT_CLASS( NoSuchFile, "org/eclipse/jgit/util/fs/NoSuchFileException" );
	JGIT_CLASS( NotDirectory, "org/eclipse/jgit/util/fs/NotDirectoryException" );
	JGIT_CLASS( NativeException, "org/eclipse/jgit/util/fs/NativeException" );
	JGIT_CLASS( OutOfMemory, "java/lang/OutOfMemoryError" );

	JGIT_CLASS ( String, "java/lang/String" );
	JGIT_METHOD( String, getBytes, "()[B");
	return 0;
}

void jgit_util_OnUnload(JNIEnv *env) {
	JGIT_DELETE_CLASS( AccessDenied );
	JGIT_DELETE_CLASS( NoSuchFile );
	JGIT_DELETE_CLASS( NotDirectory );
	JGIT_DELETE_CLASS( NativeException );
	JGIT_DELETE_CLASS( OutOfMemory );
	JGIT_DELETE_CLASS( String );
}

void jgit_ThrowOutOfMemory(JNIEnv *env) {
	(*env)->ThrowNew(env, OutOfMemory._class, NULL);
}

void jgit_ThrowErrno(JNIEnv *env, const char *path_str) {
	jclass err;
	const char *msg;

	switch (errno) {
	case EACCES:
		err = AccessDenied._class;
		msg = path_str;
		break;

	case ENOENT:
		err = NoSuchFile._class;
		msg = path_str;
		break;

	case ENOTDIR:
		err = NotDirectory._class;
		msg = path_str;
		break;

	default:
		err = NativeException._class;
		msg = strerror(errno);
		break;
	}
	(*env)->ThrowNew(env, err, msg);
}

char *jgit_GetStringNative(JNIEnv *env, jstring src) {
	jbyteArray buf;
	jthrowable err;
	jint sz;
	char *r;

	if ((*env)->EnsureLocalCapacity(env, 2) < 0)
		return NULL;

	buf = (*env)->CallObjectMethod(env, src, String.getBytes);
	err = (*env)->ExceptionOccurred(env);
	if (err) {
		(*env)->DeleteLocalRef(env, buf);
		(*env)->DeleteLocalRef(env, err);
		return NULL;
	}

	sz = (*env)->GetArrayLength(env, buf);
	r = malloc(sz + 1);
	if (!r) {
		(*env)->DeleteLocalRef(env, buf);
		jgit_ThrowOutOfMemory(env);
		return NULL;
	}

	(*env)->GetByteArrayRegion(env, buf, 0, sz, (jbyte *)r);
	(*env)->DeleteLocalRef(env, buf);
	r[sz] = 0;
	return r;
}
