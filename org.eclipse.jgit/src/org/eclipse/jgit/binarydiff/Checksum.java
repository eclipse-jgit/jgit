/*
 * Copyright (C) 2022 Yuriy Mitrofanov <mitr15fan15v@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.binarydiff;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Checksum computation class.
 *
 * @since 6.4
 */
public class Checksum {

    private final Map<Long, Integer> checksums = new HashMap<>();

    /** single hash array */
    private static final int[] single_hash = {
        0x00000000, 0xab59b4d1, 0x56b369a2, 0xfdeadd73, 0x063f6795, 0xad66d344,
        0x508c0e37, 0xfbd5bae6, 0x0c7ecf2a, 0xa7277bfb, 0x5acda688, 0xf1941259,
        0x0a41a8bf, 0xa1181c6e, 0x5cf2c11d, 0xf7ab75cc, 0x18fd9e54, 0xb3a42a85,
        0x4e4ef7f6, 0xe5174327, 0x1ec2f9c1, 0xb59b4d10, 0x48719063, 0xe32824b2,
        0x1483517e, 0xbfdae5af, 0x423038dc, 0xe9698c0d, 0x12bc36eb, 0xb9e5823a,
        0x440f5f49, 0xef56eb98, 0x31fb3ca8, 0x9aa28879, 0x6748550a, 0xcc11e1db,
        0x37c45b3d, 0x9c9defec, 0x6177329f, 0xca2e864e, 0x3d85f382, 0x96dc4753,
        0x6b369a20, 0xc06f2ef1, 0x3bba9417, 0x90e320c6, 0x6d09fdb5, 0xc6504964,
        0x2906a2fc, 0x825f162d, 0x7fb5cb5e, 0xd4ec7f8f, 0x2f39c569, 0x846071b8,
        0x798aaccb, 0xd2d3181a, 0x25786dd6, 0x8e21d907, 0x73cb0474, 0xd892b0a5,
        0x23470a43, 0x881ebe92, 0x75f463e1, 0xdeadd730, 0x63f67950, 0xc8afcd81,
        0x354510f2, 0x9e1ca423, 0x65c91ec5, 0xce90aa14, 0x337a7767, 0x9823c3b6,
        0x6f88b67a, 0xc4d102ab, 0x393bdfd8, 0x92626b09, 0x69b7d1ef, 0xc2ee653e,
        0x3f04b84d, 0x945d0c9c, 0x7b0be704, 0xd05253d5, 0x2db88ea6, 0x86e13a77,
        0x7d348091, 0xd66d3440, 0x2b87e933, 0x80de5de2, 0x7775282e, 0xdc2c9cff,
        0x21c6418c, 0x8a9ff55d, 0x714a4fbb, 0xda13fb6a, 0x27f92619, 0x8ca092c8,
        0x520d45f8, 0xf954f129, 0x04be2c5a, 0xafe7988b, 0x5432226d, 0xff6b96bc,
        0x02814bcf, 0xa9d8ff1e, 0x5e738ad2, 0xf52a3e03, 0x08c0e370, 0xa39957a1,
        0x584ced47, 0xf3155996, 0x0eff84e5, 0xa5a63034, 0x4af0dbac, 0xe1a96f7d,
        0x1c43b20e, 0xb71a06df, 0x4ccfbc39, 0xe79608e8, 0x1a7cd59b, 0xb125614a,
        0x468e1486, 0xedd7a057, 0x103d7d24, 0xbb64c9f5, 0x40b17313, 0xebe8c7c2,
        0x16021ab1, 0xbd5bae60, 0x6cb54671, 0xc7ecf2a0, 0x3a062fd3, 0x915f9b02,
        0x6a8a21e4, 0xc1d39535, 0x3c394846, 0x9760fc97, 0x60cb895b, 0xcb923d8a,
        0x3678e0f9, 0x9d215428, 0x66f4eece, 0xcdad5a1f, 0x3047876c, 0x9b1e33bd,
        0x7448d825, 0xdf116cf4, 0x22fbb187, 0x89a20556, 0x7277bfb0, 0xd92e0b61,
        0x24c4d612, 0x8f9d62c3, 0x7836170f, 0xd36fa3de, 0x2e857ead, 0x85dcca7c,
        0x7e09709a, 0xd550c44b, 0x28ba1938, 0x83e3ade9, 0x5d4e7ad9, 0xf617ce08,
        0x0bfd137b, 0xa0a4a7aa, 0x5b711d4c, 0xf028a99d, 0x0dc274ee, 0xa69bc03f,
        0x5130b5f3, 0xfa690122, 0x0783dc51, 0xacda6880, 0x570fd266, 0xfc5666b7,
        0x01bcbbc4, 0xaae50f15, 0x45b3e48d, 0xeeea505c, 0x13008d2f, 0xb85939fe,
        0x438c8318, 0xe8d537c9, 0x153feaba, 0xbe665e6b, 0x49cd2ba7, 0xe2949f76,
        0x1f7e4205, 0xb427f6d4, 0x4ff24c32, 0xe4abf8e3, 0x19412590, 0xb2189141,
        0x0f433f21, 0xa41a8bf0, 0x59f05683, 0xf2a9e252, 0x097c58b4, 0xa225ec65,
        0x5fcf3116, 0xf49685c7, 0x033df00b, 0xa86444da, 0x558e99a9, 0xfed72d78,
        0x0502979e, 0xae5b234f, 0x53b1fe3c, 0xf8e84aed, 0x17bea175, 0xbce715a4,
        0x410dc8d7, 0xea547c06, 0x1181c6e0, 0xbad87231, 0x4732af42, 0xec6b1b93,
        0x1bc06e5f, 0xb099da8e, 0x4d7307fd, 0xe62ab32c, 0x1dff09ca, 0xb6a6bd1b,
        0x4b4c6068, 0xe015d4b9, 0x3eb80389, 0x95e1b758, 0x680b6a2b, 0xc352defa,
        0x3887641c, 0x93ded0cd, 0x6e340dbe, 0xc56db96f, 0x32c6cca3, 0x999f7872,
        0x6475a501, 0xcf2c11d0, 0x34f9ab36, 0x9fa01fe7, 0x624ac294, 0xc9137645,
        0x26459ddd, 0x8d1c290c, 0x70f6f47f, 0xdbaf40ae, 0x207afa48, 0x8b234e99,
        0x76c993ea, 0xdd90273b, 0x2a3b52f7, 0x8162e626, 0x7c883b55, 0xd7d18f84,
        0x2c043562, 0x875d81b3, 0x7ab75cc0, 0xd1eee811
    };

    /**
     * Create a new checksums for source. The checksum for the <code>chunkSize</code> bytes at offset
     * <code>chunkSize</code> * count is inserted into a hash map.
     *
     * @param source
     *            seekable source.
     * @param chunkSize
     *            size of chunks.
     */
    public Checksum(SeekableSource source, int chunkSize) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(chunkSize * 2);
        int count = 0;
        while (true) {
            source.read(byteBuffer);
            byteBuffer.flip();
            if (byteBuffer.remaining() < chunkSize) {
                break;
            }
            while (byteBuffer.remaining() >= chunkSize) {
                long queryChecksum = calcChecksum(byteBuffer, chunkSize);
                checksums.put(queryChecksum, count++);
            }
            byteBuffer.compact();
        }
    }

    /**
     * Get unsigned value.
     *
     * @param signed
     *            int value for transformations.
     * @return unsigned int.
     */
    private static long getUnsigned(int signed) {
        return signed >= 0 ? signed : 2 * (long) Integer.MAX_VALUE + 2 + signed;
    }

    /**
     * Finds the checksum computed from the buffer.
     * Marks, gets, then resets the buffer.
     *
     * @param byteBuffer
     *            byte buffer.
     * @param len
     *            data length.
     * @return checksum.
     */
    public static long queryChecksum(ByteBuffer byteBuffer, int len) {
        byteBuffer.mark();
        long sum = calcChecksum(byteBuffer, len);
        byteBuffer.reset();
        return sum;
    }

    /**
     * Calculate the checksum computed from the buffer.
     * Marks, gets, then resets the buffer.
     *
     * @param byteBuffer
     *            byte buffer.
     * @param len
     *            data length.
     * @return checksum.
     */
    private static long calcChecksum(ByteBuffer byteBuffer, int len) {
        long high = 0;
        long low = 0;
        for (int i = 0; i < len; i++) {
            low += getUnsigned(single_hash[byteBuffer.get() + 128]);
            high += low;
        }
        return ((high & 0xffff) << 16) | (low & 0xffff);
    }

    /**
     * Increments a checksum.
     *
     * @param checksum
     *            initial checksum.
     * @param out
     *            byte leaving view.
     * @param in
     *            byte entering view.
     * @param chunkSize
     *            size of chunks.
     * @return new checksum.
     */
    public static long incrementChecksum(long checksum, byte out, byte in,
                                         int chunkSize) {
        long oldValue = getUnsigned(single_hash[out + 128]);
        long newValue = getUnsigned(single_hash[in + 128]);
        long low   = (((checksum) & 0xffff) - oldValue + newValue) & 0xffff;
        long high  = (((checksum) >> 16) - (oldValue * chunkSize) + low) & 0xffff;
        return (high << 16) | (low & 0xffff);
    }

    /**
     * Finds the index of a checksum.
     *
     * @param hash
     *            hash for find.
     * @return index of a checksum and -1 if not found.
     */
    public long findChecksumIndex(long hash) {
        if (!checksums.containsKey(hash)) {
            return -1;
        }
        return checksums.get(hash);
    }

}
