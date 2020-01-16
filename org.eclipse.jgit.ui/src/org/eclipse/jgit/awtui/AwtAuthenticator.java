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

package org.eclipse.jgit.awtui;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.PasswordAuthentication;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import org.eclipse.jgit.util.CachedAuthenticator;

/**
 * Basic network prompt for username/password when using AWT.
 */
public class AwtAuthenticator extends CachedAuthenticator {
	/**
	 * Install this authenticator implementation into the JVM.
	 */
	public static void install() {
		setDefault(new AwtAuthenticator());
	}

	/** {@inheritDoc} */
	@Override
	protected PasswordAuthentication promptPasswordAuthentication() {
		final GridBagConstraints gbc = new GridBagConstraints(0, 0, 1, 1, 1, 1,
				GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0);
		final Container panel = new JPanel();
		panel.setLayout(new GridBagLayout());

		final StringBuilder instruction = new StringBuilder();
		instruction.append(UIText.get().enterUsernameAndPasswordFor);
		instruction.append(" "); //$NON-NLS-1$
		if (getRequestorType() == RequestorType.PROXY) {
			instruction.append(getRequestorType());
			instruction.append(" "); //$NON-NLS-1$
			instruction.append(getRequestingHost());
			if (getRequestingPort() > 0) {
				instruction.append(":"); //$NON-NLS-1$
				instruction.append(getRequestingPort());
			}
		} else {
			instruction.append(getRequestingURL());
		}

		gbc.weightx = 1.0;
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		gbc.gridx = 0;
		panel.add(new JLabel(instruction.toString()), gbc);
		gbc.gridy++;

		gbc.gridwidth = GridBagConstraints.RELATIVE;

		// Username
		//
		final JTextField username;
		gbc.fill = GridBagConstraints.NONE;
		gbc.gridx = 0;
		gbc.weightx = 1;
		panel.add(new JLabel(UIText.get().username), gbc);

		gbc.gridx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weighty = 1;
		username = new JTextField(20);
		panel.add(username, gbc);
		gbc.gridy++;

		// Password
		//
		final JPasswordField password;
		gbc.fill = GridBagConstraints.NONE;
		gbc.gridx = 0;
		gbc.weightx = 1;
		panel.add(new JLabel(UIText.get().password), gbc);

		gbc.gridx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weighty = 1;
		password = new JPasswordField(20);
		panel.add(password, gbc);
		gbc.gridy++;

		if (JOptionPane.showConfirmDialog(null, panel,
				UIText.get().authenticationRequired, JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION) {
			return new PasswordAuthentication(username.getText(), password
					.getPassword());
		}

		return null; // cancel
	}
}
