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
