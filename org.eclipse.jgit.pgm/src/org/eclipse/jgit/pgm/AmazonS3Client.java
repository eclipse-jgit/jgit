/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import static java.lang.Integer.valueOf;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.Properties;

import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.transport.AmazonS3;
import org.kohsuke.args4j.Argument;

@Command(name = "amazon-s3-client", common = false, usage = "usage_CommandLineClientForamazonsS3Service")
class AmazonS3Client extends TextBuiltin {
	@Argument(index = 0, metaVar = "metaVar_connProp", required = true)
	private File propertyFile;

	@Argument(index = 1, metaVar = "metaVar_op", required = true)
	private String op;

	@Argument(index = 2, metaVar = "metaVar_bucket", required = true)
	private String bucket;

	@Argument(index = 3, metaVar = "metaVar_KEY", required = true)
	private String key;

	/** {@inheritDoc} */
	@Override
	protected final boolean requiresRepository() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	protected void run() throws Exception {
		final AmazonS3 s3 = new AmazonS3(properties());

		if (op == null) {
			throw die(MessageFormat.format(CLIText.get().unsupportedOperation, op));
		}
		switch (op) {
		case "get": //$NON-NLS-1$
			final URLConnection c = s3.get(bucket, key);
			int len = c.getContentLength();
			try (InputStream in = c.getInputStream()) {
				outw.flush();
				final byte[] tmp = new byte[2048];
				while (len > 0) {
					final int n = in.read(tmp);
					if (n < 0)
						throw new EOFException(MessageFormat.format(
								CLIText.get().expectedNumberOfbytes,
								valueOf(len)));
					outs.write(tmp, 0, n);
					len -= n;
				}
				outs.flush();
			}
			break;
		case "ls": //$NON-NLS-1$
		case "list": //$NON-NLS-1$
			for (String k : s3.list(bucket, key))
				outw.println(k);
			break;
		case "rm": //$NON-NLS-1$
		case "delete": //$NON-NLS-1$
			s3.delete(bucket, key);
			break;
		case "put": //$NON-NLS-1$
			try (OutputStream os = s3.beginPut(bucket, key, null, null)) {
				final byte[] tmp = new byte[2048];
				int n;
				while ((n = ins.read(tmp)) > 0)
					os.write(tmp, 0, n);
			}
			break;
		default:
			throw die(MessageFormat.format(CLIText.get().unsupportedOperation, op));
		}
	}

	private Properties properties() {
		try {
			try (InputStream in = new FileInputStream(propertyFile)) {
				final Properties p = new Properties();
				p.load(in);
				return p;
			}
		} catch (FileNotFoundException e) {
			throw die(MessageFormat.format(CLIText.get().noSuchFile, propertyFile), e);
		} catch (IOException e) {
			throw die(MessageFormat.format(CLIText.get().cannotReadBecause, propertyFile, e.getMessage()), e);
		}
	}
}
