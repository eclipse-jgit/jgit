/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com>
 * Copyright (C) 2015, Sasa Zivkov <sasa.zivkov@sap.com>
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
package org.eclipse.jgit.lfs.server.s3;

/**
 * Configuration for an Amazon AWS S3 bucket
 *
 * @since 4.3
 */
public class S3Config {
	private final String region;
	private final String bucket;
	private final String storageClass;
	private final String accessKey;
	private final String secretKey;
	private final int expirationSeconds;
	private final boolean disableSslVerify;

	/**
	 * <p>Constructor for S3Config.</p>
	 *
	 * @param region
	 *            AWS region
	 * @param bucket
	 *            S3 storage bucket
	 * @param storageClass
	 *            S3 storage class
	 * @param accessKey
	 *            access key for authenticating to AWS
	 * @param secretKey
	 *            secret key for authenticating to AWS
	 * @param expirationSeconds
	 *            period in seconds after which requests signed for this bucket
	 *            will expire
	 * @param disableSslVerify
	 *            if {@code true} disable Amazon server certificate and hostname
	 *            verification
	 */
	public S3Config(String region, String bucket, String storageClass,
			String accessKey, String secretKey, int expirationSeconds,
			boolean disableSslVerify) {
		this.region = region;
		this.bucket = bucket;
		this.storageClass = storageClass;
		this.accessKey = accessKey;
		this.secretKey = secretKey;
		this.expirationSeconds = expirationSeconds;
		this.disableSslVerify = disableSslVerify;
	}

	/**
	 * Get the <code>region</code>.
	 *
	 * @return Get name of AWS region this bucket resides in
	 */
	public String getRegion() {
		return region;
	}

	/**
	 * Get the <code>bucket</code>.
	 *
	 * @return Get S3 storage bucket name
	 */
	public String getBucket() {
		return bucket;
	}

	/**
	 * Get the <code>storageClass</code>.
	 *
	 * @return S3 storage class to use for objects stored in this bucket
	 */
	public String getStorageClass() {
		return storageClass;
	}

	/**
	 * Get the <code>accessKey</code>.
	 *
	 * @return access key for authenticating to AWS
	 */
	public String getAccessKey() {
		return accessKey;
	}

	/**
	 * Get the <code>secretKey</code>.
	 *
	 * @return secret key for authenticating to AWS
	 */
	public String getSecretKey() {
		return secretKey;
	}

	/**
	 * Get the <code>expirationSeconds</code>.
	 *
	 * @return period in seconds after which requests signed for this bucket
	 *         will expire
	 */
	public int getExpirationSeconds() {
		return expirationSeconds;
	}

	/**
	 * @return {@code true} if Amazon server certificate and hostname
	 *         verification is disabled
	 */
	boolean isDisableSslVerify() {
		return disableSslVerify;
	}

}
