/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.awtui;

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Stroke;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import org.eclipse.jgit.awtui.SwingCommitList.SwingLane;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revplot.PlotCommit;
import org.eclipse.jgit.revplot.PlotCommitList;
import org.eclipse.jgit.util.References;

/**
 * Draws a commit graph in a JTable.
 * <p>
 * This class is currently a very primitive commit visualization tool. It shows
 * a table of 3 columns:
 * <ol>
 * <li>Commit graph and short message</li>
 * <li>Author name and email address</li>
 * <li>Author date and time</li>
 * </ol>
 */
public class CommitGraphPane extends JTable {
	private static final long serialVersionUID = 1L;

	private final SwingCommitList allCommits;

	/**
	 * Create a new empty panel.
	 */
	public CommitGraphPane() {
		allCommits = new SwingCommitList();
		configureHeader();
		setShowHorizontalLines(false);
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		configureRowHeight();
	}

	private void configureRowHeight() {
		int h = 0;
		for (int i = 0; i<getColumnCount(); ++i) {
			TableCellRenderer renderer = getDefaultRenderer(getColumnClass(i));
			Component c = renderer.getTableCellRendererComponent(this,
					"ÅOj", false, false, 0, i); //$NON-NLS-1$
			h = Math.max(h, c.getPreferredSize().height);
		}
		setRowHeight(h + getRowMargin());
	}

	/**
	 * Get the commit list this pane renders from.
	 *
	 * @return the list the caller must populate.
	 */
	public PlotCommitList getCommitList() {
		return allCommits;
	}

	/** {@inheritDoc} */
	@Override
	public void setModel(TableModel dataModel) {
		if (dataModel != null && !(dataModel instanceof CommitTableModel))
			throw new ClassCastException(UIText.get().mustBeSpecialTableModel);
		super.setModel(dataModel);
	}

	/** {@inheritDoc} */
	@Override
	protected TableModel createDefaultDataModel() {
		return new CommitTableModel();
	}

	private void configureHeader() {
		final JTableHeader th = getTableHeader();
		final TableColumnModel cols = th.getColumnModel();

		final TableColumn graph = cols.getColumn(0);
		final TableColumn author = cols.getColumn(1);
		final TableColumn date = cols.getColumn(2);

		graph.setHeaderValue(""); //$NON-NLS-1$
		author.setHeaderValue(UIText.get().author);
		date.setHeaderValue(UIText.get().date);

		graph.setCellRenderer(new GraphCellRender());
		author.setCellRenderer(new NameCellRender());
		date.setCellRenderer(new DateCellRender());
	}

	class CommitTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;

		PlotCommit<SwingLane> lastCommit;

		PersonIdent lastAuthor;

		@Override
		public int getColumnCount() {
			return 3;
		}

		@Override
		public int getRowCount() {
			return allCommits != null ? allCommits.size() : 0;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			final PlotCommit<SwingLane> c = allCommits.get(rowIndex);
			switch (columnIndex) {
			case 0:
				return c;
			case 1:
				return authorFor(c);
			case 2:
				return authorFor(c);
			default:
				return null;
			}
		}

		PersonIdent authorFor(PlotCommit<SwingLane> c) {
			if (!References.isSameObject(c, lastCommit)) {
				lastCommit = c;
				lastAuthor = c.getAuthorIdent();
			}
			return lastAuthor;
		}
	}

	static class NameCellRender extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;

		@Override
		public Component getTableCellRendererComponent(final JTable table,
				final Object value, final boolean isSelected,
				final boolean hasFocus, final int row, final int column) {
			final PersonIdent pi = (PersonIdent) value;

			final String valueStr;
			if (pi != null)
				valueStr = pi.getName() + " <" + pi.getEmailAddress() + ">"; //$NON-NLS-1$ //$NON-NLS-2$
			else
				valueStr = ""; //$NON-NLS-1$
			return super.getTableCellRendererComponent(table, valueStr,
					isSelected, hasFocus, row, column);
		}
	}

	static class DateCellRender extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;

		private final DateFormat fmt = new SimpleDateFormat(
				"yyyy-MM-dd HH:mm:ss"); //$NON-NLS-1$

		@Override
		public Component getTableCellRendererComponent(final JTable table,
				final Object value, final boolean isSelected,
				final boolean hasFocus, final int row, final int column) {
			final PersonIdent pi = (PersonIdent) value;

			final String valueStr;
			if (pi != null)
				valueStr = fmt.format(pi.getWhen());
			else
				valueStr = ""; //$NON-NLS-1$
			return super.getTableCellRendererComponent(table, valueStr,
					isSelected, hasFocus, row, column);
		}
	}

	static class GraphCellRender extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;

		private final AWTPlotRenderer renderer = new AWTPlotRenderer(this);

		PlotCommit<SwingLane> commit;

		@Override
		@SuppressWarnings("unchecked")
		public Component getTableCellRendererComponent(final JTable table,
				final Object value, final boolean isSelected,
				final boolean hasFocus, final int row, final int column) {
			super.getTableCellRendererComponent(table, value, isSelected,
					hasFocus, row, column);
			commit = (PlotCommit<SwingLane>) value;
			return this;
		}

		@Override
		protected void paintComponent(Graphics inputGraphics) {
			if (inputGraphics == null)
				return;
			renderer.paint(inputGraphics, commit);
		}
	}

	static final Stroke[] strokeCache;

	static {
		strokeCache = new Stroke[4];
		for (int i = 1; i < strokeCache.length; i++)
			strokeCache[i] = new BasicStroke(i);
	}

	static Stroke stroke(int width) {
		if (width < strokeCache.length)
			return strokeCache[width];
		return new BasicStroke(width);
	}

}
