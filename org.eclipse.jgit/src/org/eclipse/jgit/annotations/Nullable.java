/*
 * Copyright (C) 2015, Google Inc.
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

package org.eclipse.jgit.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks types that can hold the value {@code null} at run time.
 * <p>
 * Unlike {@code org.eclipse.jdt.annotation.Nullable}, this has run-time
 * retention, allowing the annotation to be recognized by
 * <a href="https://github.com/google/guice/wiki/UseNullable">Guice</a>. Unlike
 * {@code javax.annotation.Nullable}, this does not involve importing new classes
 * to a standard (Java EE) package, so it can be deployed in an OSGi container
 * without running into
 * <a href="http://wiki.osgi.org/wiki/Split_Packages">split-package</a>
 * <a href="https://gerrit-review.googlesource.com/50112">problems</a>.
 * <p>
 * You can use this annotation to qualify a type in a method signature or local
 * variable declaration. The entity whose type has this annotation is allowed to
 * hold the value {@code null} at run time. This allows annotation based null
 * analysis to infer that
 * <ul>
 * <li>Binding a {@code null} value to the entity is legal.
 * <li>Dereferencing the entity is unsafe and can trigger a
 * {@code NullPointerException}.
 * </ul>
 * <p>
 * To avoid a dependency on Java 8, this annotation does not use
 * {@link Target @Target} {@code TYPE_USE}. That may change when JGit starts
 * requiring Java 8.
 * <p>
 * <b>Warning:</b> Please do not use this annotation on arrays. Different
 * annotation processors treat {@code @Nullable Object[]} differently: some
 * treat it as an array of nullable objects, for consistency with versions of
 * {@code Nullable} defined with {@code @Target} {@code TYPE_USE}, while others
 * treat it as a nullable array of objects. JGit therefore avoids using this
 * annotation on arrays altogether.
 *
 * @see <a href=
 *      "http://types.cs.washington.edu/checker-framework/current/checker-framework-manual.html#faq-array-syntax-meaning">
 *      The checker-framework manual</a>
 * @since 4.2
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
public @interface Nullable {
	// marker annotation with no members
}
