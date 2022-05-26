/*
 * Copyright (C) 2022, Workday Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.SimpleTimeZone;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.jgit.util.Hex;
import org.eclipse.jgit.util.HttpSupport;

/**
 * Utility class for signing requests to AWS service endpoints using the V4 signing protocol.
 *
 * Reference implementation: <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/samples/AWSS3SigV4JavaSamples.zip">AWSS3SigV4JavaSamples.zip</a>
 *
 * @see <a href="https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html">AWS Signature Version 4</a>
 */
public class AwsRequestSignerV4 {

    /** AWS version 4 signing algorithm. **/
    private static final String ALGORITHM = "HMAC-SHA256";

    /** Message Authentication Code (MAC) algorithm name. **/
    private static final String MAC_ALGORITHM = "HmacSHA256";

    /** AWS version 4 signing scheme. **/
    private static final String SCHEME = "AWS4";

    /** AWS version 4 terminator string. **/
    private static final String TERMINATOR = "aws4_request";

    /** SHA-256 hash of an empty request body. **/
    private static final String EMPTY_BODY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    /** Date format for the 'x-amz-date' header. **/
    private static final SimpleDateFormat AMZ_DATE_FORMAT;

    /** Date format for the string-to-sign's scope. **/
    private static final SimpleDateFormat SCOPE_DATE_FORMAT;

    static {
        AMZ_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        AMZ_DATE_FORMAT.setTimeZone(new SimpleTimeZone(0, "UTC"));
        SCOPE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");
        SCOPE_DATE_FORMAT.setTimeZone(new SimpleTimeZone(0, "UTC"));
    }

    private AwsRequestSignerV4() {
        throw new UnsupportedOperationException("Cannot instantiate helper class: " + getClass().getSimpleName());
    }

    /**
     * Sign the provided request with an AWS4 signature as the 'Authorization' header.
     *
     * @param httpURLConnection
     *            The request to sign.
     * @param queryParameters
     *            The query parameters being sent in the request.
     * @param data
     *            The data being sent in the request body.
     * @param serviceName
     *            The signing name of the AWS service (e.g. "s3").
     * @param regionName
     *            The name of the AWS region that will handle the request (e.g. "us-east-1").
     * @param awsAccessKey
     *            The user's AWS Access Key.
     * @param awsSecretKey
     *            The user's AWS Secret Key.
     */
    public static void sign(HttpURLConnection httpURLConnection,
            Map<String, String> queryParameters, byte[] data, String serviceName,
            String regionName, String awsAccessKey, char[] awsSecretKey) {
        // get request headers
        Map<String, String> headers = new HashMap<>();
        httpURLConnection.getRequestProperties().forEach((headerName, headerValues) ->
            headers.put(headerName, String.join(",", headerValues)));

        // add required content headers
        int contentLength = data == null ? 0 : data.length;
        String bodyHash;
        if (contentLength > 0) {
            byte[] contentHash = hash(data);
            bodyHash = Hex.toHexString(contentHash);
            headers.put("content-length", "" + contentLength);
        }
        else {
            bodyHash = EMPTY_BODY_SHA256;
        }
        headers.put("x-amz-content-sha256", bodyHash);

        // add the 'x-amz-date' header
        Date now = new Date();
        String amzDate = AMZ_DATE_FORMAT.format(now);
        headers.put("x-amz-date", amzDate);

        // add the 'host' header
        URL endpointUrl = httpURLConnection.getURL();
        int port = endpointUrl.getPort();
        String hostHeader = (port > -1)
            ? endpointUrl.getHost().concat(":" + port)
            : endpointUrl.getHost();
        headers.put("Host", hostHeader);

        // construct the canonicalized request
        String canonicalizedHeaderNames = getCanonicalizeHeaderNames(headers);
        String canonicalizedHeaders = getCanonicalizedHeaderString(headers);
        String canonicalizedQueryParameters = getCanonicalizedQueryString(queryParameters);
        String httpMethod = httpURLConnection.getRequestMethod();
        String canonicalRequest = httpMethod + "\n" +
            getCanonicalizedResourcePath(endpointUrl) + "\n" +
            canonicalizedQueryParameters + "\n" +
            canonicalizedHeaders + "\n" +
            canonicalizedHeaderNames + "\n" +
            bodyHash;

        // construct the string-to-sign
        String scopeDate = SCOPE_DATE_FORMAT.format(now);
        String scope = scopeDate + "/" + regionName + "/" + serviceName + "/" + TERMINATOR;
        String stringToSign = SCHEME + "-" + ALGORITHM + "\n" +
            amzDate + "\n" +
            scope + "\n" +
            Hex.toHexString(hash(canonicalRequest.getBytes(StandardCharsets.UTF_8)));

        // compute the signing key
        byte[] secretKey = (SCHEME + new String(awsSecretKey)).getBytes();
        byte[] dateKey = signStringWithKey(scopeDate, secretKey);
        byte[] regionKey = signStringWithKey(regionName, dateKey);
        byte[] serviceKey = signStringWithKey(serviceName, regionKey);
        byte[] signingKey = signStringWithKey(TERMINATOR, serviceKey);
        byte[] signature = signStringWithKey(stringToSign, signingKey);

        // construct the authorization header
        String credentialsAuthorizationHeader = "Credential=" + awsAccessKey + "/" + scope;
        String signedHeadersAuthorizationHeader = "SignedHeaders=" + canonicalizedHeaderNames;
        String signatureAuthorizationHeader = "Signature=" + Hex.toHexString(signature);
        String authorizationHeader = SCHEME + "-" + ALGORITHM + " "
            + credentialsAuthorizationHeader + ", "
            + signedHeadersAuthorizationHeader + ", "
            + signatureAuthorizationHeader;

        // Copy back the updated request headers
        headers.forEach(httpURLConnection::setRequestProperty);

        // Add the 'authorization' header
        httpURLConnection.setRequestProperty("authorization", authorizationHeader);
    }

    /**
     * Construct a string listing all request headers in sorted case-insensitive order, separated by a ';'.
     *
     * @param headers
     *            Map containing all request headers.
     *
     * @return String that lists all request headers in sorted case-insensitive order, separated by a ';'.
     */
    private static String getCanonicalizeHeaderNames(Map<String, String> headers) {
        return headers.keySet().stream()
            .map(String::toLowerCase)
            .sorted()
            .collect(Collectors.joining(";"));
    }

    /**
     * Constructs the canonical header string for a request.
     *
     * @param headers
     *            Map containing all request headers.
     *
     * @return The canonical headers with values for the request.
     */
    private static String getCanonicalizedHeaderString(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        headers.keySet().stream()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .forEach(key -> {
                String header = key.toLowerCase().replaceAll("\\s+", " ");
                String value = headers.get(key).replaceAll("\\s+", " ");
                sb.append(header).append(":").append(value).append("\n");
            });
        return sb.toString();
    }

    /**
     * Constructs the canonicalized resource path for an AWS service endpoint.
     *
     * @param url
     *            The AWS service endpoint URL, including the path to any resource.
     *
     * @return The canonicalized resource path for the AWS service endpoint.
     */
    private static String getCanonicalizedResourcePath(URL url) {
        if (url == null) {
            return "/";
        }
        String path = url.getPath();
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String encodedPath = HttpSupport.urlEncode(path, true);
        if (encodedPath.startsWith("/")) {
            return encodedPath;
        }
        else {
            return "/".concat(encodedPath);
        }
    }

    /**
     * Constructs the canonicalized query string for a request.
     *
     * @param queryParameters
     *            The query parameters in the request.
     *
     * @return The canonicalized query string for the request.
     */
    public static String getCanonicalizedQueryString(Map<String, String> queryParameters) {
        if (queryParameters == null || queryParameters.isEmpty()) {
            return "";
        }
        return queryParameters.keySet().stream()
            .sorted()
            .map(key ->
                HttpSupport.urlEncode(key, false)
                + "="
                + HttpSupport.urlEncode(queryParameters.get(key), false))
            .collect(Collectors.joining("&"));
    }

    /**
     * Hashes the provided byte array using the SHA-256 algorithm.
     *
     * @param data
     *            The byte array to hash.
     *
     * @return Hashed string contents of the provided byte array.
     */
    public static byte[] hash(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(data);
            return md.digest();
        }
        catch (Exception e) {
            throw new RuntimeException("Unable to hash byte array with SHA-256 algorithm: " + e.getMessage(), e);
        }
    }

    /**
     * Signs the provided string data using the specified key.
     *
     * @param stringToSign
     *            The string data to sign.
     * @param key
     *            The key material of the secret key.
     *
     * @return Signed string data.
     */
    private static byte[] signStringWithKey(String stringToSign, byte[] key) {
        try {
            byte[] data = stringToSign.getBytes(StandardCharsets.UTF_8);
            Mac mac = Mac.getInstance(MAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, MAC_ALGORITHM));
            return mac.doFinal(data);
        }
        catch (Exception e) {
            throw new RuntimeException("Unable to calculate a request signature: " + e.getMessage(), e);
        }
    }

}
