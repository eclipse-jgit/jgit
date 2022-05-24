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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Various Http helper routines.
 * Based on <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/samples/AWSS3SigV4JavaSamples.zip">AWSS3SigV4JavaSamples.zip</a>
 */
public class HttpUtils {

    /**
     * Translates the provided URL into application/x-www-form-urlencoded format.
     *
     * @param url
     *            The URL to translate.
     * @param keepPathSlash
     *            Whether or not to keep "/" in the URL (i.e. don't translate them to "%2F").
     *
     * @return The translated URL.
     */
    public static String urlEncode(String url, boolean keepPathSlash) {
        String encoded;
        try {
            encoded = URLEncoder.encode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding is not supported.", e);
        }
        if ( keepPathSlash ) {
            encoded = encoded.replace("%2F", "/");
        }
        return encoded;
    }

}
