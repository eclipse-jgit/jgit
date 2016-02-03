/*******************************************************************************
 * Copyright (c) 2016 Yuriy Rotmistrov
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *
 *
 *******************************************************************************/
package org.eclipse.jgit.patch.binary;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Simple filter input stream intended to skip all bytes till byte 0x0A ('\n')
 */
public class SkipFirstLineInputStream extends FilterInputStream
{
    private boolean firstLineSkipped;

	/**
	 * @param in
	 *            the underlying input stream
	 */
    public SkipFirstLineInputStream(InputStream in)
    {
        super(in);
    }

    public int read() throws IOException
    {
        skipFirstLine();
        return super.read();
    }

    public final int read(byte[] data,
                          int offset,
                          int len) throws IOException
    {
        skipFirstLine();
        return super.read(data, offset, len);
    }

    public int available() throws IOException
    {
        skipFirstLine();
        return super.available();
    }

    private void skipFirstLine() throws IOException
    {
        if (!firstLineSkipped)
        {
            int b;
            while ((b = super.read()) != '\n' && b != -1)
            {
				// Skip byte
            }
            firstLineSkipped = true;
        }
    }

}
