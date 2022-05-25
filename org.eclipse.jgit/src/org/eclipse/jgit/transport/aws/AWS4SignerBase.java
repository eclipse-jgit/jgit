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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SimpleTimeZone;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.jgit.util.Hex;
import org.eclipse.jgit.util.HttpSupport;

/**
 * Common methods and properties for all AWS4 signer variants.
 * Based on <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/samples/AWSS3SigV4JavaSamples.zip">AWSS3SigV4JavaSamples.zip</a>
 *
 * @see <a href="https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html">AWS Signature Version 4</a>
 */
public abstract class AWS4SignerBase {

    /** SHA256 hash of an empty request body **/
    public static final String EMPTY_BODY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    /** String-to-sign scheme. **/
    public static final String SCHEME = "AWS4";

    /** String-to-sign algorithm. **/
    public static final String ALGORITHM = "HMAC-SHA256";

    /** String-to-sign scope terminator. **/
    public static final String TERMINATOR = "aws4_request";
    
    /** Format string for the date in the 'x-amz-date' header and string-to-sign. **/
    public static final String ISO8601_BASIC_FORMAT = "yyyyMMdd'T'HHmmss'Z'";

    /** Format string for the date in the string-to-sign's scope. **/
    public static final String DATE_STRING_FORMAT = "yyyyMMdd";

    /** Endpoint URL of the request being signed. **/
    protected URL endpointUrl;

    /** HTTP method of the request being signed. **/
    protected String httpMethod;

    /** Name of the AWS service that the request is for. **/
    protected String serviceName;

    /** Name of the AWS region that the request is for. **/
    protected String regionName;

    /** Date format for the 'x-amz-date' header and string-to-sign. **/
    protected final SimpleDateFormat dateTimeFormat;

    /** Date format for the string-to-sign's scope. **/
    protected final SimpleDateFormat dateStampFormat;
    
    /**
     * Create a new AWS V4 signer.
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
    public AWS4SignerBase(URL endpointUrl, String httpMethod,
            String serviceName, String regionName) {
        this.endpointUrl = endpointUrl;
        this.httpMethod = httpMethod;
        this.serviceName = serviceName;
        this.regionName = regionName;
        
        dateTimeFormat = new SimpleDateFormat(ISO8601_BASIC_FORMAT);
        dateTimeFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
        dateStampFormat = new SimpleDateFormat(DATE_STRING_FORMAT);
        dateStampFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
    }
    
    /**
     * Returns the canonical collection of header names that will be included in
     * the signature. For AWS4, all header names must be included in the process
     * in sorted canonicalized order.
     *
     * @param headers
     *            Map containing all headers in the request.
     *
     * @return The canonical collection of header names that will be included in
     *            the signature.
     */
    protected static String getCanonicalizeHeaderNames(Map<String, String> headers) {
        List<String> sortedHeaders = new ArrayList<String>();
        sortedHeaders.addAll(headers.keySet());
        Collections.sort(sortedHeaders, String.CASE_INSENSITIVE_ORDER);

        StringBuilder buffer = new StringBuilder();
        for (String header : sortedHeaders) {
            if (buffer.length() > 0) buffer.append(";");
            buffer.append(header.toLowerCase());
        }

        return buffer.toString();
    }
    
    /**
     * Computes the canonical headers with values for the request. For AWS4, all
     * headers must be included in the signing process.
     *
     * @param headers
     *            Map containing all headers in the request.
     *
     * @return The canonical headers with values for the request.
     */
    protected static String getCanonicalizedHeaderString(Map<String, String> headers) {
        if ( headers == null || headers.isEmpty() ) {
            return "";
        }
        
        // step1: sort the headers by case-insensitive order
        List<String> sortedHeaders = new ArrayList<String>();
        sortedHeaders.addAll(headers.keySet());
        Collections.sort(sortedHeaders, String.CASE_INSENSITIVE_ORDER);

        // step2: form the canonical header:value entries in sorted order. 
        // Multiple white spaces in the values should be compressed to a single 
        // space.
        StringBuilder buffer = new StringBuilder();
        for (String key : sortedHeaders) {
            buffer.append(key.toLowerCase().replaceAll("\\s+", " ") + ":" + headers.get(key).replaceAll("\\s+", " "));
            buffer.append("\n");
        }

        return buffer.toString();
    }
    
    /**
     * Returns the canonical request string to go into the signer process; this
     * consists of several canonical sub-parts.
     *
     * @param endpoint
     *            The service endpoint, including the path to any resource.
     * @param httpMethod
     *            The HTTP verb for the request, e.g. GET.
     * @param queryParameters
     *            The query parameters of the request.
     * @param canonicalizedHeaderNames
     *            The canonicalized header names for the request.
     * @param canonicalizedHeaders
     *            The canonicalized headers for the request.
     * @param bodyHash
     *            Precomputed SHA256 hash of the request body content.
     *
     * @return The canonical request string to go into the signer process.
     */
    protected static String getCanonicalRequest(URL endpoint, String httpMethod,
            String queryParameters, String canonicalizedHeaderNames,
            String canonicalizedHeaders, String bodyHash) {
        String canonicalRequest =
            httpMethod + "\n" +
            getCanonicalizedResourcePath(endpoint) + "\n" +
            queryParameters + "\n" +
            canonicalizedHeaders + "\n" +
            canonicalizedHeaderNames + "\n" +
            bodyHash;
        return canonicalRequest;
    }
    
    /**
     * Returns the canonicalized resource path for the service endpoint.
     *
     * @param endpoint
     *            The service endpoint, including the path to any resource.
     *
     * @return The canonicalized resource path for the service endpoint.
     */
    protected static String getCanonicalizedResourcePath(URL endpoint) {
        if ( endpoint == null ) {
            return "/";
        }
        String path = endpoint.getPath();
        if ( path == null || path.isEmpty() ) {
            return "/";
        }
        
        String encodedPath = HttpSupport.urlEncode(path, true);
        if (encodedPath.startsWith("/")) {
            return encodedPath;
        } else {
            return "/".concat(encodedPath);
        }
    }
    
    /**
     * Examines the specified query string parameters and returns a
     * canonicalized form.
     * <p>
     * The canonicalized query string is formed by first sorting all the query
     * string parameters, then URI encoding both the key and value and then
     * joining them, in order, separating key value pairs with an '&'.
     *
     * @param parameters
     *            The query string parameters to be canonicalized.
     *
     * @return A canonicalized form for the specified query string parameters.
     */
    public static String getCanonicalizedQueryString(Map<String, String> parameters) {
        if ( parameters == null || parameters.isEmpty() ) {
            return "";
        }
        
        SortedMap<String, String> sorted = new TreeMap<String, String>();

        Iterator<Map.Entry<String, String>> pairs = parameters.entrySet().iterator();
        while (pairs.hasNext()) {
            Map.Entry<String, String> pair = pairs.next();
            String key = pair.getKey();
            String value = pair.getValue();
            sorted.put(HttpSupport.urlEncode(key, false), HttpSupport.urlEncode(value, false));
        }

        StringBuilder builder = new StringBuilder();
        pairs = sorted.entrySet().iterator();
        while (pairs.hasNext()) {
            Map.Entry<String, String> pair = pairs.next();
            builder.append(pair.getKey());
            builder.append("=");
            builder.append(pair.getValue());
            if (pairs.hasNext()) {
                builder.append("&");
            }
        }

        return builder.toString();
    }

    /**
     * Returns the string-to-sign needed to produce an authentication signature.
     *
     * @param scheme
     *            String-to-sign scheme (e.g. AWS4).
     * @param algorithm
     *            String-to-sign algorithm (e.g. HMAC-SHA256).
     * @param dateTime
     *            Timestamp sent in the 'x-amz-date' header.
     * @param scope
     *            Scope that binds the resulting signature to a specific date, an AWS Region, and a service.
     * @param canonicalRequest
     *            The canonical request.
     *
     * @return The string-to-sign needed to produce an authentication signature.
     */
    protected static String getStringToSign(String scheme, String algorithm, String dateTime, String scope, String canonicalRequest) {
        String stringToSign =
            scheme + "-" + algorithm + "\n" +
            dateTime + "\n" +
            scope + "\n" +
            Hex.toHexString(hash(canonicalRequest));
        return stringToSign;
    }

    /**
     * Hashes the string contents (assumed to be UTF-8) using the SHA-256
     * algorithm.
     *
     * @param text
     *            The string to hash
     *
     * @return Hashed string contents of the provided text.
     */
    public static byte[] hash(String text) {
        return hash(text.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Hashes the byte array using the SHA-256 algorithm.
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
        } catch (Exception e) {
            throw new RuntimeException("Unable to compute hash while signing request: " + e.getMessage(), e);
        }
    }

    /**
     * Signs the provided string data using the specified key and algorithm.
     *
     * @param stringData
     *            The string data to sign.
     * @param key
     *            The key material of the secret key.
     * @param algorithm
     *            The name of the secret-key algorithm to be associated with the given key material.
     *
     * @return Signed string data.
     */
    protected static byte[] sign(String stringData, byte[] key, String algorithm) {
        try {
            byte[] data = stringData.getBytes("UTF-8");
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Unable to calculate a request signature: " + e.getMessage(), e);
        }
    }
}
