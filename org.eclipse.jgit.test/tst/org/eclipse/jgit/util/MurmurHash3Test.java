/*
 * Copyright (C) 2021, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class MurmurHash3Test {

    @Test
    public void testUnseededEmptyString() {
        String str = "";
        byte[] data = str.getBytes(StandardCharsets.UTF_8);
        int actual = MurmurHash3.hash32x86(data, 0, data.length, 0);
        assertEquals(0x00000000, actual);
    }

    @Test
    public void testUnseededString1() {
        String str = "Hello world!";
        byte[] data = str.getBytes(StandardCharsets.UTF_8);
        int actual = MurmurHash3.hash32x86(data, 0, data.length, 0);
        assertEquals(0x627b0c2c, actual);
    }

    @Test
    public void testUnseededString2() {
        String str = "The quick brown fox jumps over the lazy dog";
        byte[] data = str.getBytes(StandardCharsets.UTF_8);
        int actual = MurmurHash3.hash32x86(data, 0, data.length, 0);
        assertEquals(0x2e4ff723, actual);
    }

    @Test
    public void testUnseededString3() {
        String str = "你好世界";
        byte[] data = str.getBytes(StandardCharsets.UTF_8);
        int actual = MurmurHash3.hash32x86(data, 0, data.length, 0);
        assertEquals(0xa5979109, actual);
    }

    @Test
    public void testUnseededString4() {
        String str = "Привет мир";
        byte[] data = str.getBytes(StandardCharsets.UTF_8);
        int actual = MurmurHash3.hash32x86(data, 0, data.length, 0);
        assertEquals(0xb70e5010, actual);
    }
}
