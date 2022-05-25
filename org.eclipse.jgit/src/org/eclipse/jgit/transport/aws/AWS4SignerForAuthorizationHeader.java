/*
 * Copyright (C) 2022, Workday Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport.aws;

import java.net.URL;
import java.util.Date;
import java.util.Map;

import org.eclipse.jgit.util.Hex;

/**
 * AWS4 signer to sign requests to Amazon S3 using an 'Authorization' header.
 * Based on <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/samples/AWSS3SigV4JavaSamples.zip">AWSS3SigV4JavaSamples.zip</a>
 *
 * @see <a href="https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html">AWS Signature Version 4</a>
 */
public class AWS4SignerForAuthorizationHeader extends AWS4SignerBase {

    /**
     * Construct a new {@link AWS4SignerForAuthorizationHeader} instance.
     *
     * @param endpointUrl
     *            The service endpoint, including the path to any resource.
     * @param httpMethod
     *            The HTTP verb for the request, e.g. GET.
     * @param serviceName
     *            The signing name of the service, e.g. 's3'.
     * @param regionName
     *            The system name of the AWS region associated with the
     *            endpoint, e.g. us-east-1.
     */
    public AWS4SignerForAuthorizationHeader(URL endpointUrl, String httpMethod,
    		String serviceName, String regionName) {
        super(endpointUrl, httpMethod, serviceName, regionName);
    }
    
    /**
     * Computes an AWS4 signature for a request, ready for inclusion as an
     * 'Authorization' header.
     * 
     * @param headers
     *            The request headers; 'Host' and 'X-Amz-Date' will be added to
     *            this set.
     * @param queryParameters
     *            Any query parameters that will be added to the endpoint. The
     *            parameters should be specified in canonical format.
     * @param data
     *            The data being sent in the request body.
     * @param awsAccessKey
     *            The user's AWS Access Key.
     * @param awsSecretKey
     *            The user's AWS Secret Key.
     *
     * @return The computed authorization string for the request. This value
     *         needs to be set as the header 'Authorization' on the subsequent
     *         HTTP request.
     */
    public String computeSignature(Map<String, String> headers,
                                   Map<String, String> queryParameters,
                                   final byte[] data,
                                   String awsAccessKey,
                                   char[] awsSecretKey) {
        // add required content headers
        final int contentLength = data == null ? 0 : data.length;
        final String bodyHash;
        if (contentLength > 0) {
            final byte[] contentHash = AWS4SignerBase.hash(data);
            bodyHash = Hex.toHexString(contentHash);
            headers.put("content-length", "" + contentLength);
        }
        else {
            bodyHash = AWS4SignerBase.EMPTY_BODY_SHA256;
        }
        headers.put("x-amz-content-sha256", bodyHash);

        // get the date and time for the subsequent request, and convert
        // to ISO 8601 format for use in signature generation
        Date now = new Date();
        String dateTimeStamp = dateTimeFormat.format(now);

        // update the headers with required 'x-amz-date' and 'host' values
        headers.put("x-amz-date", dateTimeStamp);
        
        String hostHeader = endpointUrl.getHost();
        int port = endpointUrl.getPort();
        if ( port > -1 ) {
            hostHeader.concat(":" + Integer.toString(port));
        }
        headers.put("Host", hostHeader);
        
        // canonicalize the headers; we need the set of header names as well as the
        // names and values to go into the signature process
        String canonicalizedHeaderNames = getCanonicalizeHeaderNames(headers);
        String canonicalizedHeaders = getCanonicalizedHeaderString(headers);
        
        // if any query string parameters have been supplied, canonicalize them
        String canonicalizedQueryParameters = getCanonicalizedQueryString(queryParameters);
        
        // canonicalize the various components of the request
        String canonicalRequest = getCanonicalRequest(endpointUrl, httpMethod,
                canonicalizedQueryParameters, canonicalizedHeaderNames,
                canonicalizedHeaders, bodyHash);
        
        // construct the string to be signed
        String dateStamp = dateStampFormat.format(now);
        String scope =  dateStamp + "/" + regionName + "/" + serviceName + "/" + TERMINATOR;
        String stringToSign = getStringToSign(SCHEME, ALGORITHM, dateTimeStamp, scope, canonicalRequest);
        
        // compute the signing key
        byte[] kSecret = (SCHEME + new String(awsSecretKey)).getBytes();
        byte[] kDate = sign(dateStamp, kSecret, "HmacSHA256");
        byte[] kRegion = sign(regionName, kDate, "HmacSHA256");
        byte[] kService = sign(serviceName, kRegion, "HmacSHA256");
        byte[] kSigning = sign(TERMINATOR, kService, "HmacSHA256");
        byte[] signature = sign(stringToSign, kSigning, "HmacSHA256");
        
        String credentialsAuthorizationHeader =
                "Credential=" + awsAccessKey + "/" + scope;
        String signedHeadersAuthorizationHeader =
                "SignedHeaders=" + canonicalizedHeaderNames;
        String signatureAuthorizationHeader =
                "Signature=" + Hex.toHexString(signature);

        String authorizationHeader = SCHEME + "-" + ALGORITHM + " "
                + credentialsAuthorizationHeader + ", "
                + signedHeadersAuthorizationHeader + ", "
                + signatureAuthorizationHeader;

        return authorizationHeader;
    }
}
