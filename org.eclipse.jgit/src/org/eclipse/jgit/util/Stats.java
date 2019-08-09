/*
 * Copyright (C) 2019, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.util;

/**
 * Simple double statistics, computed incrementally, variance and standard
 * deviation using Welford's online algorithm, see
 * https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Welford's_online_algorithm
 *
 * @since 5.1.9
 */
public class Stats {
	private int n = 0;

	private double avg = 0.0;

	private double min = 0.0;

	private double max = 0.0;

	private double sum = 0.0;

	/**
	 * Add a value
	 *
	 * @param x
	 *            value
	 */
	public void add(double x) {
		n++;
		min = n == 1 ? x : Math.min(min, x);
		max = n == 1 ? x : Math.max(max, x);
		double d = x - avg;
		avg += d / n;
		sum += d * d * (n - 1) / n;
	}

	/**
	 * @return number of the added values
	 */
	public int count() {
		return n;
	}

	/**
	 * @return minimum of the added values
	 */
	public double min() {
		if (n < 1) {
			return Double.NaN;
		}
		return min;
	}

	/**
	 * @return maximum of the added values
	 */
	public double max() {
		if (n < 1) {
			return Double.NaN;
		}
		return max;
	}

	/**
	 * @return average of the added values
	 */

	public double avg() {
		if (n < 1) {
			return Double.NaN;
		}
		return avg;
	}

	/**
	 * @return variance of the added values
	 */
	public double var() {
		if (n < 2) {
			return Double.NaN;
		}
		return sum / (n - 1);
	}

	/**
	 * @return standard deviation of the added values
	 */
	public double stddev() {
		return Math.sqrt(this.var());
	}
}
