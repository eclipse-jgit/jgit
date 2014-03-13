/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.revplot;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevFlag;

/**
 * Basic commit graph renderer for graphical user interfaces.
 * <p>
 * Lanes are drawn as columns left-to-right in the graph, and the commit short
 * message is drawn to the right of the lane lines for this cell. It is assumed
 * that the commits are being drawn as rows of some sort of table.
 * <p>
 * Client applications can subclass this implementation to provide the necessary
 * drawing primitives required to display a commit graph. Most of the graph
 * layout is handled by this class, allowing applications to implement only a
 * handful of primitive stubs.
 * <p>
 * This class is suitable for us within an AWT TableCellRenderer or within a SWT
 * PaintListener registered on a Table instance. It is meant to rubber stamp the
 * graphics necessary for one row of a plotted commit list.
 * <p>
 * Subclasses should call {@link #paintCommit(PlotCommit, int)} after they have
 * otherwise configured their instance to draw one commit into the current
 * location.
 * <p>
 * All drawing methods assume the coordinate space for the current commit's cell
 * starts at (upper left corner is) 0,0. If this is not true (like say in SWT)
 * the implementation must perform the cell offset computations within the
 * various draw methods.
 *
 * @param <TLane>
 *            type of lane being used by the application.
 * @param <TColor>
 *            type of color object used by the graphics library.
 */
public abstract class AbstractPlotRenderer<TLane extends PlotLane, TColor> {
	private static final int LANE_WIDTH = 14;

	private static final int LINE_WIDTH = 2;

	private static final int LEFT_PAD = 2;

	/**
	 * Paint one commit using the underlying graphics library.
	 *
	 * @param commit
	 *            the commit to render in this cell. Must not be null.
	 * @param h
	 *            total height (in pixels) of this cell.
	 */
	protected void paintCommit(final PlotCommit<TLane> commit, final int h) {
		final int dotSize = computeDotSize(h);
		final TLane myLane = commit.getLane();
		final int myLaneX = laneC(myLane);
		final TColor myColor = laneColor(myLane);

		int maxCenter = myLaneX;
		for (final TLane passingLane : (TLane[]) commit.passingLanes) {
			final int cx = laneC(passingLane);
			final TColor c = laneColor(passingLane);
			drawLine(c, cx, 0, cx, h, LINE_WIDTH);
			maxCenter = Math.max(maxCenter, cx);
		}

		final int nParent = commit.getParentCount();
		for (int i = 0; i < nParent; i++) {
			final PlotCommit<TLane> p;
			final TLane pLane;
			final TColor pColor;
			final int cx;

			p = (PlotCommit<TLane>) commit.getParent(i);
			pLane = p.getLane();
			if (pLane == null)
				continue;

			pColor = laneColor(pLane);
			cx = laneC(pLane);

			if (Math.abs(myLaneX - cx) > LANE_WIDTH) {
				if (myLaneX < cx) {
					final int ix = cx - LANE_WIDTH / 2;
					drawLine(pColor, myLaneX, h / 2, ix, h / 2, LINE_WIDTH);
					drawLine(pColor, ix, h / 2, cx, h, LINE_WIDTH);
				} else {
					final int ix = cx + LANE_WIDTH / 2;
					drawLine(pColor, myLaneX, h / 2, ix, h / 2, LINE_WIDTH);
					drawLine(pColor, ix, h / 2, cx, h, LINE_WIDTH);
				}
			} else {
				drawLine(pColor, myLaneX, h / 2, cx, h, LINE_WIDTH);
			}
			maxCenter = Math.max(maxCenter, cx);
		}

		final int dotX = myLaneX - dotSize / 2 - 1;
		final int dotY = (h - dotSize) / 2;

		if (commit.getChildCount() > 0)
			drawLine(myColor, myLaneX, 0, myLaneX, dotY, LINE_WIDTH);

		if (commit.has(RevFlag.UNINTERESTING))
			drawBoundaryDot(dotX, dotY, dotSize, dotSize);
		else
			drawCommitDot(dotX, dotY, dotSize, dotSize);

		int textx = Math.max(maxCenter + LANE_WIDTH / 2, dotX + dotSize) + 8;
		int n = commit.refs.length;
		for (int i = 0; i < n; ++i) {
			textx += drawLabel(textx + dotSize, h/2, commit.refs[i]);
		}

		final String msg = commit.getShortMessage();
		drawText(msg, textx + dotSize, h / 2);
	}

	/**
	 * Draw a decoration for the Ref ref at x,y
	 *
	 * @param x
	 *            left
	 * @param y
	 *            top
	 * @param ref
	 *            A peeled ref
	 * @return width of label in pixels
	 */
	protected abstract int drawLabel(int x, int y, Ref ref);

	private int computeDotSize(final int h) {
		int d = (int) (Math.min(h, LANE_WIDTH) * 0.50f);
		d += (d & 1);
		return d;
	}

	/**
	 * Obtain the color reference used to paint this lane.
	 * <p>
	 * Colors returned by this method will be passed to the other drawing
	 * primitives, so the color returned should be application specific.
	 * <p>
	 * If a null lane is supplied the return value must still be acceptable to a
	 * drawing method. Usually this means the implementation should return a
	 * default color.
	 *
	 * @param myLane
	 *            the current lane. May be null.
	 * @return graphics specific color reference. Must be a valid color.
	 */
	protected abstract TColor laneColor(TLane myLane);

	/**
	 * Draw a single line within this cell.
	 *
	 * @param color
	 *            the color to use while drawing the line.
	 * @param x1
	 *            starting X coordinate, 0 based.
	 * @param y1
	 *            starting Y coordinate, 0 based.
	 * @param x2
	 *            ending X coordinate, 0 based.
	 * @param y2
	 *            ending Y coordinate, 0 based.
	 * @param width
	 *            number of pixels wide for the line. Always at least 1.
	 */
	protected abstract void drawLine(TColor color, int x1, int y1, int x2,
			int y2, int width);

	/**
	 * Draw a single commit dot.
	 * <p>
	 * Usually the commit dot is a filled oval in blue, then a drawn oval in
	 * black, using the same coordinates for both operations.
	 *
	 * @param x
	 *            upper left of the oval's bounding box.
	 * @param y
	 *            upper left of the oval's bounding box.
	 * @param w
	 *            width of the oval's bounding box.
	 * @param h
	 *            height of the oval's bounding box.
	 */
	protected abstract void drawCommitDot(int x, int y, int w, int h);

	/**
	 * Draw a single boundary commit (aka uninteresting commit) dot.
	 * <p>
	 * Usually a boundary commit dot is a light gray oval with a white center.
	 *
	 * @param x
	 *            upper left of the oval's bounding box.
	 * @param y
	 *            upper left of the oval's bounding box.
	 * @param w
	 *            width of the oval's bounding box.
	 * @param h
	 *            height of the oval's bounding box.
	 */
	protected abstract void drawBoundaryDot(int x, int y, int w, int h);

	/**
	 * Draw a single line of text.
	 * <p>
	 * The font and colors used to render the text are left up to the
	 * implementation.
	 *
	 * @param msg
	 *            the text to draw. Does not contain LFs.
	 * @param x
	 *            first pixel from the left that the text can be drawn at.
	 *            Character data must not appear before this position.
	 * @param y
	 *            pixel coordinate of the centerline of the text.
	 *            Implementations must adjust this coordinate to account for the
	 *            way their implementation handles font rendering.
	 */
	protected abstract void drawText(String msg, int x, int y);

	private int laneX(final PlotLane myLane) {
		final int p = myLane != null ? myLane.getPosition() : 0;
		return LEFT_PAD + LANE_WIDTH * p;
	}

	private int laneC(final PlotLane myLane) {
		return laneX(myLane) + LANE_WIDTH / 2;
	}
}
