/*
 * Copyright (C) 2010, Sasa Zivkov <sasa.zivkov@sap.com>
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

package org.eclipse.jgit.nls;

import java.util.Locale;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import junit.framework.TestCase;


public class TestNLS extends TestCase {

	public void testNLSLocale() {
		NLS.setLocale(NLS.ROOT_LOCALE);
		GermanTranslatedBundle bundle = GermanTranslatedBundle.get();
		assertEquals(NLS.ROOT_LOCALE, bundle.effectiveLocale());

		NLS.setLocale(Locale.GERMAN);
		bundle = GermanTranslatedBundle.get();
		assertEquals(Locale.GERMAN, bundle.effectiveLocale());
	}

	public void testJVMDefaultLocale() {
		Locale.setDefault(NLS.ROOT_LOCALE);
		NLS.useJVMDefaultLocale();
		GermanTranslatedBundle bundle = GermanTranslatedBundle.get();
		assertEquals(NLS.ROOT_LOCALE, bundle.effectiveLocale());

		Locale.setDefault(Locale.GERMAN);
		NLS.useJVMDefaultLocale();
		bundle = GermanTranslatedBundle.get();
		assertEquals(Locale.GERMAN, bundle.effectiveLocale());
	}

	public void testThreadTranslationBundleInheritance() throws InterruptedException {

		class T extends Thread {
			GermanTranslatedBundle bundle;
			@Override
			public void run() {
				bundle = GermanTranslatedBundle.get();
			}
		}

		NLS.setLocale(NLS.ROOT_LOCALE);
		GermanTranslatedBundle mainThreadsBundle = GermanTranslatedBundle.get();
		T t = new T();
		t.start();
		t.join();
		assertSame(mainThreadsBundle, t.bundle);

		NLS.setLocale(Locale.GERMAN);
		mainThreadsBundle = GermanTranslatedBundle.get();
		t = new T();
		t.start();
		t.join();
		assertSame(mainThreadsBundle, t.bundle);
	}

	public void testParallelThreadsWithDifferentLocales() throws InterruptedException {

		final CyclicBarrier barrier = new CyclicBarrier(2);

		class T extends Thread {
			Locale locale;
			GermanTranslatedBundle bundle;
			Exception e;

			T(Locale locale) {
				this.locale = locale;
			}

			@Override
			public void run() {
				try {
					NLS.setLocale(locale);
					barrier.await(); // wait for the other thread to set its locale
					bundle = GermanTranslatedBundle.get();
				} catch (InterruptedException e) {
					this.e = e;
				} catch (BrokenBarrierException e) {
					this.e = e;
				}
			}
		}

		T t1 = new T(NLS.ROOT_LOCALE);
		T t2 = new T(Locale.GERMAN);
		t1.start();
		t2.start();
		t1.join();
		t2.join();

		assertNull("t1 was interrupted or barrier was broken", t1.e);
		assertNull("t2 was interrupted or barrier was broken", t2.e);
		assertEquals(NLS.ROOT_LOCALE, t1.bundle.effectiveLocale());
		assertEquals(Locale.GERMAN, t2.bundle.effectiveLocale());
	}
}
