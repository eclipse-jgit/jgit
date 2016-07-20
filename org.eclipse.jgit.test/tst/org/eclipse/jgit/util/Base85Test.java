/*
 * Copyright (C) 2016, Nadav Cohen <nadavcoh@gmail.com>
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

import org.junit.Test;

import static org.eclipse.jgit.util.Base85.decode85;
import static org.eclipse.jgit.util.Base85.encode85;
import static org.junit.Assert.*;

public class Base85Test {

    private static char[] ILLEGAL_CHARS = {'[', ']', '"', '\'', '/', ',', '.', '\\'};

    @Test
    public void testFailOnIllegalChars() {
        for (char c : ILLEGAL_CHARS) {
            try {
                String s = c + "2345";
                byte[] bytes = s.getBytes();
                decode85(bytes, 0, bytes.length, 5);
                fail("Accepted bad string in decode");
            } catch (IllegalArgumentException fail) {
                // Expected
            }
        }
    }

    @Test
    public void failOnBadLength() {
        try {
            byte[] bytes = "hi".getBytes();
            decode85(bytes, 0, bytes.length, 2);
            fail("Accepted string with length not multiple of 5");
        } catch (IllegalArgumentException fail) {
            // Expected
        }
    }

    @Test
    public void testEncodeDecode() {
        byte[] buffer = "joke".getBytes();
        byte[] encoded = encode85(buffer, 0, buffer.length);

        assertEquals(5, encoded.length);

        byte[] decoded = decode85(encoded, 0, encoded.length, 4);
        assertArrayEquals("joke".getBytes(), decoded);
    }

    @Test
    public void testEncodePadding() {
        byte[] buffer = "hello".getBytes();
        byte[] encoded = encode85(buffer, 0, buffer.length);

        assertEquals(10, encoded.length);

        byte[] decoded = decode85(encoded, 0, encoded.length, 5);
        assertArrayEquals("hello".getBytes(), decoded);

    }
}
