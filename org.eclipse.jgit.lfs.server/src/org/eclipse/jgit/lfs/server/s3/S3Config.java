/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com>
 * Copyright (C) 2015, Sasa Zivkov <sasa.zivkov@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs.server.s3;

/**
 * Configuration for an Amazon AWS S3 bucket
 *
 * @since 4.3
 */
public class S3Config {
	private final String hostname;
	private final String region;
	private final String bucket;
	private final String storageClass;
	private final String accessKey;
	private final String secretKey;
	private final int expirationSeconds;
	private final boolean disableSslVerify;

	/**
	 * <p>
	 * Constructor for S3Config.
	 * </p>
	 *
	 * @param hostname
	 *            S3 API host
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
	 * @since 5.8
	 */
	public S3Config(String hostname, String region, String bucket, String storageClass,
			String accessKey, String secretKey, int expirationSeconds,
			boolean disableSslVerify) {
		this.hostname = hostname;
		this.region = region;
		this.bucket = bucket;
		this.storageClass = storageClass;
		this.accessKey = accessKey;
		this.secretKey = secretKey;
		this.expirationSeconds = expirationSeconds;
		this.disableSslVerify = disableSslVerify;
	}

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
		this(String.format("s3-%s.amazonaws.com", region), region, bucket, //$NON-NLS-1$
				storageClass, accessKey, secretKey, expirationSeconds,
				disableSslVerify);
	}

	/**
	 * Get the <code>hostname</code>.
	 *
	 * @return Get the S3 API host
	 * @since 5.8
	 */
	public String getHostname() {
		return hostname;
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
