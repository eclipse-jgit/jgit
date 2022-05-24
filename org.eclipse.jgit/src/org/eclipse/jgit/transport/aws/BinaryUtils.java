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

import java.util.Locale;

/**
 * Utilities for encoding and decoding binary data to and from different forms.
 * Based on <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/samples/AWSS3SigV4JavaSamples.zip">AWSS3SigV4JavaSamples.zip</a>
 */
public class BinaryUtils {

    /**
     * Converts byte data to a Hex-encoded string.
     *
     * @param data
     *            data to hex encode.
     *
     * @return hex-encoded string.
     */
    public static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (int i = 0; i < data.length; i++) {
            String hex = Integer.toHexString(data[i]);
            if (hex.length() == 1) {
                // Append leading zero.
                sb.append("0");
            } else if (hex.length() == 8) {
                // Remove ff prefix from negative numbers.
                hex = hex.substring(6);
            }
            sb.append(hex);
        }
        return sb.toString().toLowerCase(Locale.getDefault());
    }

}
