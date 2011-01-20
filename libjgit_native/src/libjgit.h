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

#ifndef INCLUDE_libjgit_h
#define INCLUDE_libjgit_h 1

#include <jni.h>

#define JGIT_CLASS(dst, name)                          \
  do {                                                 \
    jclass c = (*(env))->FindClass((env), (name));     \
    if (!c) {                                          \
      (*(env))->ExceptionClear((env));                 \
      return -1;                                       \
    }                                                  \
    dst._class = (*(env))->NewWeakGlobalRef((env), c); \
    (*(env))->DeleteLocalRef((env), c);                \
  } while (0)

#define JGIT_DELETE_CLASS(dst)                        \
  do {                                                \
    (*(env))->DeleteWeakGlobalRef((env), dst._class); \
    dst._class = NULL;                                \
  } while (0)

#define JGIT_METHOD(c, name, sig)                             \
  do {                                                        \
    jmethodID m;                                              \
                                                              \
    m = (*(env))->GetMethodID((env), c._class, #name, (sig)); \
    if (!m) {                                                 \
      (*(env))->ExceptionClear((env));                        \
      return -1;                                              \
    }                                                         \
    c.name = m;                                               \
  } while (0)

#define JGIT_GET_FIELD(c, name, sig)                         \
  do {                                                       \
    jfieldID f;                                              \
                                                             \
    f = (*(env))->GetFieldID((env), c._class, #name, (sig)); \
    if (!f) {                                                \
      (*(env))->ExceptionClear((env));                       \
      return -1;                                             \
    }                                                        \
    c.name = f;                                              \
  } while (0)

#define JGIT_FIELD(c, type, name)  JGIT_FIELD_##type(c, name)
#define JGIT_FIELD_int(c, name)    JGIT_GET_FIELD(c, name, "I")
#define JGIT_FIELD_long(c, name)   JGIT_GET_FIELD(c, name, "J")


#define JGIT_ALLOC(dst)  (*(env))->AllocObject((env), dst._class)


#define JGIT_SET(obj, c, type, name, val) \
  JGIT_SET_##type((obj), c, name, (val))

#define JGIT_SET_int(obj, c, name, val) \
  (*(env))->SetIntField((env), (obj), c.name, (jint)(val))

#define JGIT_SET_long(obj, c, name, val) \
  (*(env))->SetLongField((env), (obj), c.name, (jlong)(val))


extern void jgit_ThrowErrno(JNIEnv *, const char *);
extern void jgit_ThrowOutOfMemory(JNIEnv *);
extern char *jgit_GetStringNative(JNIEnv *, jstring);

extern int jgit_util_OnLoad(JNIEnv *);
extern void jgit_util_OnUnload(JNIEnv *);

extern int jgit_lstat_OnLoad(JNIEnv *);
extern void jgit_lstat_OnUnload(JNIEnv *);

#endif
