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

#include "libjgit.h"
#include "org_eclipse_jgit_util_fs_FileAccessNative.h"

#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdlib.h>

static struct {
	jclass _class;

	jfieldID atime;
	jfieldID ctime;
	jfieldID mtime;

	jfieldID atime_nsec;
	jfieldID ctime_nsec;
	jfieldID mtime_nsec;

	jfieldID dev;
	jfieldID ino;
	jfieldID mode;
	jfieldID uid;
	jfieldID gid;
	jfieldID size;
} FileInfo;

int jgit_lstat_OnLoad(JNIEnv *env) {
	JGIT_CLASS( FileInfo, "org/eclipse/jgit/util/fs/FileInfo" );
	JGIT_FIELD( FileInfo, int,  atime );
	JGIT_FIELD( FileInfo, int,  ctime );
	JGIT_FIELD( FileInfo, int,  mtime );
	JGIT_FIELD( FileInfo, int,  atime_nsec );
	JGIT_FIELD( FileInfo, int,  ctime_nsec );
	JGIT_FIELD( FileInfo, int,  mtime_nsec );
	JGIT_FIELD( FileInfo, int,  dev );
	JGIT_FIELD( FileInfo, int,  ino );
	JGIT_FIELD( FileInfo, int,  mode );
	JGIT_FIELD( FileInfo, int,  uid );
	JGIT_FIELD( FileInfo, int,  gid );
	JGIT_FIELD( FileInfo, long, size );
	return 0;
}

void jgit_lstat_OnUnload(JNIEnv *env) {
	JGIT_DELETE_CLASS( FileInfo );
}

JNIEXPORT jobject JNICALL
Java_org_eclipse_jgit_util_fs_FileAccessNative_lstatImp(
		JNIEnv *env, jclass clazz, jbyteArray path) {
	jobject r = NULL;
	char *path_str;
	struct stat st;

	path_str = jgit_GetStringNative(env, path);
	if (!path_str)
		return NULL;

	if (lstat(path_str, &st)) {
		jgit_ThrowErrno(env, path_str);
		goto done;
	}

	r = JGIT_ALLOC( FileInfo );
	if (!r)
		goto done;

	JGIT_SET( r, FileInfo, int, atime, st.st_atime );
	JGIT_SET( r, FileInfo, int, ctime, st.st_ctime );
	JGIT_SET( r, FileInfo, int, mtime, st.st_mtime );

#ifdef NO_NSEC
	JGIT_SET( r, FileInfo, int, atime_nsec, 0 );
	JGIT_SET( r, FileInfo, int, ctime_nsec, 0 );
	JGIT_SET( r, FileInfo, int, mtime_nsec, 0 );
#else
# ifdef USE_ST_TIMESPEC
	JGIT_SET( r, FileInfo, int, atime_nsec, st.st_atimespec.tv_nsec );
	JGIT_SET( r, FileInfo, int, ctime_nsec, st.st_ctimespec.tv_nsec );
	JGIT_SET( r, FileInfo, int, mtime_nsec, st.st_mtimespec.tv_nsec );
# else
	JGIT_SET( r, FileInfo, int, atime_nsec, st.st_atim.tv_nsec);
	JGIT_SET( r, FileInfo, int, ctime_nsec, st.st_ctim.tv_nsec);
	JGIT_SET( r, FileInfo, int, mtime_nsec, st.st_mtim.tv_nsec);
# endif
#endif

	JGIT_SET( r, FileInfo, int,  dev,  st.st_dev);
	JGIT_SET( r, FileInfo, int,  ino,  st.st_ino);
	JGIT_SET( r, FileInfo, int,  mode, st.st_mode);
	JGIT_SET( r, FileInfo, int,  uid,  st.st_uid);
	JGIT_SET( r, FileInfo, int,  gid,  st.st_gid);
	JGIT_SET( r, FileInfo, long, size, st.st_size);

done:
	free(path_str);
	return r;
}
