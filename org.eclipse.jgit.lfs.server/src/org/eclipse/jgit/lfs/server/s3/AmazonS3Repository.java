/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com>
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

import java.io.IOException;
import java.net.URL;
import java.util.Date;

import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.server.LargeFileRepository;
import org.eclipse.jgit.lfs.server.Response;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;

/**
 * Repository storing large objects in the Amazon S3 storage
 *
 * @since 4.2
 */
public class AmazonS3Repository implements LargeFileRepository {

	private final Region region;

	private final String bucket;

	private AWSCredentials credentials;

	private int presignedUrlValidity;

	/**
	 * @param region
	 * @param bucket
	 * @param credentials
	 * @param presignedUrlValidity
	 *            validity period for presigned URLs, in seconds
	 */
	public AmazonS3Repository(String region, String bucket,
			AWSCredentials credentials, int presignedUrlValidity) {
		this.region = Region.getRegion(Regions.fromName(region));
		this.bucket = bucket;
		this.credentials = credentials;
		this.presignedUrlValidity = presignedUrlValidity;
	}

	@Override
	public long getLength(AnyLongObjectId id) throws IOException {
		// return 0;
		return -1;
	}

	@Override
	public Response.Action getDownloadAction(AnyLongObjectId id) {
		AmazonS3Client s3client = new AmazonS3Client(credentials);
		s3client.setRegion(region);
        Date expiration = new Date();
		long expiresAt = expiration.getTime() + 1000 * presignedUrlValidity;
		expiration.setTime(expiresAt);

		GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(
				bucket, id.getName());
		req.setMethod(HttpMethod.GET);
		req.setExpiration(expiration);

		URL url = s3client.generatePresignedUrl(req);
		System.out.println("URL: " + url);
		Response.Action a = new Response.Action();
		a.href = url.toString();
		return a;
	}

	@Override
	public Response.Action getUploadAction(AnyLongObjectId id) {
		AmazonS3Client s3client = new AmazonS3Client(credentials);
		s3client.setRegion(region);
        Date expiration = new Date();
		long expiresAt = expiration.getTime() + 1000 * presignedUrlValidity;
		expiration.setTime(expiresAt);

		GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(
				bucket, id.getName());
		req.setMethod(HttpMethod.PUT);
		req.setExpiration(expiration);

		URL url = s3client.generatePresignedUrl(req);
		Response.Action a = new Response.Action();
		a.href = url.toString();
		return a;
	}

	@Override
	public Response.Action getVerifyAction(AnyLongObjectId id) {
		// TODO Auto-generated method stub
		return null;
	}
}
