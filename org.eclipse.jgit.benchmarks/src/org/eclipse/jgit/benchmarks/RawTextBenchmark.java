/*
 * Copyright (C) 2022, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import static org.eclipse.jgit.diff.RawText.getBufferSize;
import static org.eclipse.jgit.diff.RawText.isBinary;
import static org.eclipse.jgit.diff.RawText.isCrLfText;

@State(Scope.Thread)
public class RawTextBenchmark {

	@State(Scope.Benchmark)
	public static class BenchmarkState {

		@Param({"1", "2", "3", "4", "5", "6"})
		int testIndex;

		@Param({"false", "true"})
		boolean complete;

		byte[] bytes;

		@Setup
		public void setupBenchmark() {
			switch (testIndex) {
				case 1: {
					byte[] tmpBytes = "a".repeat(102400).getBytes();
					bytes = tmpBytes;
					break;
				}
				case 2: {
					byte[] tmpBytes = "a".repeat(102400).getBytes();
					byte[] tmpBytes2 = new byte[tmpBytes.length + 1];
					System.arraycopy(tmpBytes, 0, tmpBytes2, 0, tmpBytes.length);
					tmpBytes2[500] = '\0';
					tmpBytes2[tmpBytes.length] = '\0';
					bytes = tmpBytes2;
					break;
				}
				case 3: {
					byte[] tmpBytes = "a".repeat(102400).getBytes();
					byte[] tmpBytes2 = new byte[tmpBytes.length + 1];
					System.arraycopy(tmpBytes, 0, tmpBytes2, 0, tmpBytes.length);
					tmpBytes2[500] = '\r';
					tmpBytes2[tmpBytes.length] = '\r';
					bytes = tmpBytes2;
					break;
				}
				case 4: {
					byte[] tmpBytes = "a".repeat(102400).getBytes();
					byte[] tmpBytes2 = new byte[tmpBytes.length + 1];
					System.arraycopy(tmpBytes, 0, tmpBytes2, 0, tmpBytes.length);
					tmpBytes2[499] = '\r';
					tmpBytes2[500] = '\n';
					tmpBytes2[tmpBytes.length - 1] = '\r';
					tmpBytes2[tmpBytes.length] = '\n';
					bytes = tmpBytes2;
					break;
				}
				case 5: {
					byte[] tmpBytes = "a".repeat(102400).getBytes();
					tmpBytes[0] = '\0';
					bytes = tmpBytes;
					break;
				}
				case 6: {
					byte[] tmpBytes = "a".repeat(102400).getBytes();
					tmpBytes[0] = '\r';
					bytes = tmpBytes;
					break;
				}
				default:
			}
		}

		@TearDown
		public void teardown() {
		}
	}

	@Benchmark
	@BenchmarkMode({Mode.AverageTime})
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
	@Measurement(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
	@Fork(1)
	public void testIsCrLfTextOld(Blackhole blackhole, BenchmarkState state) {
		blackhole.consume(
				isCrLfTextOld(
						state.bytes,
						state.bytes.length,
						state.complete
				)
		);
	}

	@Benchmark
	@BenchmarkMode({Mode.AverageTime})
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
	@Measurement(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
	@Fork(1)
	public void testIsCrLfTextNewCandidate1(Blackhole blackhole, BenchmarkState state) {
		blackhole.consume(
				isCrLfTextNewCandidate1(
						state.bytes,
						state.bytes.length,
						state.complete
				)
		);
	}

	@Benchmark
	@BenchmarkMode({Mode.AverageTime})
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
	@Measurement(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
	@Fork(1)
	public void testIsCrLfTextNewCandidate2(Blackhole blackhole, BenchmarkState state) {
		blackhole.consume(
				isCrLfTextNewCandidate2(
						state.bytes,
						state.bytes.length,
						state.complete
				)
		);
	}

	@Benchmark
	@BenchmarkMode({Mode.AverageTime})
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
	@Measurement(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
	@Fork(1)
	public void testIsCrLfTextNewCandidate3(Blackhole blackhole, BenchmarkState state) {
		blackhole.consume(
				isCrLfTextNewCandidate3(
						state.bytes,
						state.bytes.length,
						state.complete
				)
		);
	}

	@Benchmark
	@BenchmarkMode({Mode.AverageTime})
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
	@Measurement(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
	@Fork(1)
	public void testIsCrLfTextNew(Blackhole blackhole, BenchmarkState state) {
		blackhole.consume(
				isCrLfText(
						state.bytes,
						state.bytes.length,
						state.complete
				)
		);
	}

	@Benchmark
	@BenchmarkMode({Mode.AverageTime})
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
	@Measurement(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
	@Fork(1)
	public void testIsBinaryOld(Blackhole blackhole, BenchmarkState state) {
		blackhole.consume(
				isBinaryOld(
						state.bytes,
						state.bytes.length,
						state.complete
				)
		);
	}


	@Benchmark
	@BenchmarkMode({Mode.AverageTime})
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
	@Measurement(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
	@Fork(1)
	public void testIsBinaryNew(Blackhole blackhole, BenchmarkState state) {
		blackhole.consume(
				isBinary(
						state.bytes,
						state.bytes.length,
						state.complete
				)
		);
	}


	/**
	 * Determine heuristically whether a byte array represents binary (as
	 * opposed to text) content.
	 *
	 * @param raw
	 *			the raw file content.
	 * @param length
	 *			number of bytes in {@code raw} to evaluate. This should be
	 *			{@code raw.length} unless {@code raw} was over-allocated by
	 *			the caller.
	 * @param complete
	 *			whether {@code raw} contains the whole data
	 * @return true if raw is likely to be a binary file, false otherwise
	 * @since 6.0
	 */
	public static boolean isBinaryOld(byte[] raw, int length, boolean complete) {
		// Similar heuristic as C Git. Differences:
		// - limited buffer size; may be only the beginning of a large blob
		// - no counting of printable vs. non-printable bytes < 0x20 and 0x7F
		int maxLength = getBufferSize();
		boolean isComplete = complete;
		if (length > maxLength) {
			// We restrict the length in all cases to getBufferSize() to get
			// predictable behavior. Sometimes we load streams, and sometimes we
			// have the full data in memory. With streams, we never look at more
			// than the first getBufferSize() bytes. If we looked at more when
			// we have the full data, different code paths in JGit might come to
			// different conclusions.
			length = maxLength;
			isComplete = false;
		}
		byte last = 'x'; // Just something inconspicuous.
		for (int ptr = 0; ptr < length; ptr++) {
			byte curr = raw[ptr];
			if (isBinary(curr, last)) {
				return true;
			}
			last = curr;
		}
		if (isComplete) {
			// Buffer contains everything...
			return last == '\r'; // ... so this must be a lone CR
		}
		return false;
	}

	/**
	 * Determine heuristically whether a byte array represents text content
	 * using CR-LF as line separator.
	 *
	 * @param raw	  the raw file content.
	 * @param length   number of bytes in {@code raw} to evaluate.
	 * @param complete whether {@code raw} contains the whole data
	 * @return {@code true} if raw is likely to be CR-LF delimited text,
	 * {@code false} otherwise
	 * @since 6.0
	 */
	public static boolean isCrLfTextOld(byte[] raw, int length, boolean complete) {
		boolean has_crlf = false;
		byte last = 'x'; // Just something inconspicuous
		for (int ptr = 0; ptr < length; ptr++) {
			byte curr = raw[ptr];
			if (isBinary(curr, last)) {
				return false;
			}
			if (curr == '\n' && last == '\r') {
				has_crlf = true;
			}
			last = curr;
		}
		if (last == '\r') {
			if (complete) {
				// Lone CR: it's binary after all.
				return false;
			}
			// Tough call. If the next byte, which we don't have, would be a
			// '\n', it'd be a CR-LF text, otherwise it'd be binary. Just decide
			// based on what we already scanned; it wasn't binary until now.
		}
		return has_crlf;
	}

	/**
	 * Determine heuristically whether a byte array represents text content
	 * using CR-LF as line separator.
	 *
	 * @param raw
	 *			the raw file content.
	 * @param length
	 *			number of bytes in {@code raw} to evaluate.
	 * @return {@code true} if raw is likely to be CR-LF delimited text,
	 *		 {@code false} otherwise
	 * @param complete
	 *			whether {@code raw} contains the whole data
	 * @since 6.0
	 */
	public static boolean isCrLfTextNewCandidate1(byte[] raw, int length, boolean complete) {
		boolean has_crlf = false;

		// first detect empty
		if (length <= 0) {
			return false;
		}

		// next detect '\0'
		for (int reversePtr = length - 1; reversePtr >= 0; --reversePtr) {
			if (raw[reversePtr] == '\0') {
				return false;
			}
		}

		// if '\r' be last, then if complete then return non-crlf
		if (raw[length - 1] == '\r' && complete) {
			return false;
		}

		for (int ptr = 0; ptr < length - 1; ptr++) {
			byte curr = raw[ptr];
			if (curr == '\r') {
				byte next = raw[ptr + 1];
				if (next != '\n') {
					return false;
				}
				// else
				// we have crlf here
				has_crlf = true;
				// as next is '\n', it can never be '\r', just skip it from next check
				++ptr;
			}
		}

		return has_crlf;
	}

	/**
	 * Determine heuristically whether a byte array represents text content
	 * using CR-LF as line separator.
	 *
	 * @param raw
	 *			the raw file content.
	 * @param length
	 *			number of bytes in {@code raw} to evaluate.
	 * @return {@code true} if raw is likely to be CR-LF delimited text,
	 *		 {@code false} otherwise
	 * @param complete
	 *			whether {@code raw} contains the whole data
	 * @since 6.0
	 */
	public static boolean isCrLfTextNewCandidate2(byte[] raw, int length, boolean complete) {
		boolean has_crlf = false;

		// first detect empty
		if (length <= 0) {
			return false;
		}

		// if '\r' be last, then if complete then return non-crlf
		byte last = raw[length - 1];
		if (last == '\0' || last == '\r' && complete) {
			return false;
		}

		for (int ptr = 0; ptr < length - 1; ptr++) {
			byte b = raw[ptr];
			switch (b) {
				case  '\0':
					return false;
				case '\r': {
					++ptr;
					b = raw[ptr];
					if (b != '\n') {
						return false;
					}
					// else
					// we have crlf here
					has_crlf = true;
					// as next is '\n', it can never be '\r', just skip it from next check
					break;
				}
				default:
					// do nothing;
					break;
			}
		}

		return has_crlf;
	}

	/**
	 * Determine heuristically whether a byte array represents text content
	 * using CR-LF as line separator.
	 *
	 * @param raw
	 *			the raw file content.
	 * @param length
	 *			number of bytes in {@code raw} to evaluate.
	 * @return {@code true} if raw is likely to be CR-LF delimited text,
	 *		 {@code false} otherwise
	 * @param complete
	 *			whether {@code raw} contains the whole data
	 * @since 6.0
	 */
	public static boolean isCrLfTextNewCandidate3(byte[] raw, int length, boolean complete) {
		boolean has_crlf = false;

		int ptr = -1;
		byte current;
		while (ptr < length - 2) {
			current = raw[++ptr];
			if ('\0' == current || '\r' == current && (raw[++ptr] != '\n' || !(has_crlf = true))) {
				return false;
			}
		}

		if (ptr == length - 2) {
			// if '\r' be last, then if isComplete then return binary
			current = raw[++ptr];
			if('\0' == current || '\r' == current && complete){
				return false;
			}
		}

		return has_crlf;
	}


	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include(RawTextBenchmark.class.getSimpleName())
				.forks(1).jvmArgs("-ea").build();
		new Runner(opt).run();
	}
}
