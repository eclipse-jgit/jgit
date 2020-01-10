/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.eclipse.jgit.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class IOUtil {

	protected final List<File> tempFiles = new ArrayList<>();
	protected static final Map<String, byte[]> images = new HashMap<>();

	public void cleanup() {
		for (final File tempFile : tempFiles) {
			try {
				FileUtils.delete(tempFile, FileUtils.RECURSIVE | FileUtils.SKIP_MISSING | FileUtils.IGNORE_ERRORS);
			} catch (IOException e) {
			}
		}
	}

	public File createTempDirectory() throws IOException {
		final File temp = File.createTempFile("temp", Long.toString(System.nanoTime()));
		if (!(temp.delete())) {
			throw new IOException("Could not delete temp file: " + temp.getAbsolutePath());
		}

		if (!(temp.mkdir())) {
			throw new IOException("Could not create temp directory: " + temp.getAbsolutePath());
		}

		tempFiles.add(temp);

		return temp;
	}

	public byte[] getImage1() {
		return images.computeIfAbsent("image1", k -> drawImage(Color.white, Color.black, Color.yellow, "Image 1"));
	}

	public byte[] getImage2() {
		return images.computeIfAbsent("image2", k -> drawImage(Color.green, Color.gray, Color.cyan, "Image 2"));
	}

	public byte[] getImage3() {
		return images.computeIfAbsent("image3", k -> drawImage(Color.magenta, Color.orange, Color.red, "Image 3"));
	}

	private byte[] drawImage(final Color c1, final Color c2, final Color c3,
							 final String message) {
		int width = 250;
		int height = 250;

		// Constructs a BufferedImage of one of the predefined image types.
		final BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		// Create a graphics which can be used to draw into the buffered image
		Graphics2D g2d = bufferedImage.createGraphics();

		// fill all the image with white
		g2d.setColor(c1);
		g2d.fillRect(0, 0, width, height);

		// create a circle with black
		g2d.setColor(c2);
		g2d.fillOval(0, 0, width, height);

		// create a string with yellow
		g2d.setColor(c3);
		g2d.drawString(message, 50, 120);

		// Disposes of this graphics context and releases any system resources that it is using.
		g2d.dispose();

		byte[] imageInByte;
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			ImageIO.write(bufferedImage, "png", baos);
			baos.flush();
			imageInByte = baos.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return imageInByte;
	}
}
