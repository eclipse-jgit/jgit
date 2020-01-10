/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.config;

import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Encapsulates a collection of Java System Properties by name and value.
 * Includes handy methods for providing and identifying application-provided
 * default values and converting from Strings to other common types.
 */
public class ConfigProperties {

	private final Map<String, String> configuredValues;
	private final List<ConfigProperty> configSummary = new ArrayList<>();

	/**
	 * Creates a new container of configured values from which specific config
	 * properties can be obtained.
	 * 
	 * @param configuredValues The configured values, which may have been hardcoded
	 *                         in a Map, read from a config file, or whatever.
	 */
	public ConfigProperties(Map<String, String> configuredValues) {
		this.configuredValues = checkNotNull("configuredValues", configuredValues);
	}

	/**
	 * Creates a new container of configured values from which specific config
	 * properties can be obtained.
	 * 
	 * @param configuredValues The configured values, which may have been read from
	 *                         a properties file, or obtained from
	 *                         {@link System#getProperties()}. If the property set
	 *                         contains entries whose key and value are not both
	 *                         Strings, these entries will be ignored.
	 */
	public ConfigProperties(Properties configuredValues) {
		Map<String, String> stringProperties = new HashMap<>();
		for (String key : configuredValues.stringPropertyNames()) {
			stringProperties.put(key, configuredValues.getProperty(key));
		}
		this.configuredValues = stringProperties;
	}

	/**
	 * Returns the ConfigProperty instance corresponding to the configured value of
	 * the given property, or the default if no configured value exists.
	 * 
	 * @param name         the property name. Must not be null.
	 * @param defaultValue the value to use if no configured value exists. May be
	 *                     null.
	 * @return
	 */
	public ConfigProperty get(String name, String defaultValue) {
		String val = configuredValues.get(name);
		ConfigProperty cp;
		if (val == null || val.trim().length() == 0) {
			cp = new ConfigProperty(name, defaultValue, true);
		} else {
			cp = new ConfigProperty(name, val.trim(), false);
		}
		configSummary.add(cp);
		return cp;
	}

	/**
	 * Returns a multi-line string containing a list of all the properties that were
	 * retrieved from this instance, in the order they were retrieved. Does not list
	 * unused values from the map given in the constructor, since these may contain
	 * a lot of unrelated information (for example, when using
	 * System.getProperties()). This is useful for printing a summary of the
	 * configuration in a given subsystem. It also helps users learn about available
	 * configuration values.
	 * 
	 * @param heading a line of text to print before listing the configuration
	 *                values
	 */
	public String getConfigurationSummary(String heading) {
		final String newLine = System.getProperty("line.separator");
		StringBuilder sb = new StringBuilder(heading);
		for (ConfigProperty cp : configSummary) {
			sb.append(newLine).append(cp);
		}
		return sb.toString();
	}

	public static class ConfigProperty {

		private final String name;
		private final String value;
		private final boolean isDefault;

		ConfigProperty(String name, String value, boolean isDefault) {
			this.name = name;
			this.value = value;
			this.isDefault = isDefault;
		}

		/**
		 * Returns the name (map key) of this property.
		 */
		public String getName() {
			return name;
		}

		/**
		 * Returns the value of this property, which may or may not have been the
		 * default value.
		 * 
		 * @return the property value. Will be null if both the configured value was
		 *         missing and the default was given as null.
		 * @see #isDefault()
		 */
		public String getValue() {
			return value;
		}

		/**
		 * Returns false if this value appeared among the user-supplied values; false if
		 * it came from the application-provided default.
		 * 
		 * @return whether this value is a default
		 */
		public boolean isDefault() {
			return isDefault;
		}

		/**
		 * Returns the boolean value of this property, converting from string using the
		 * same rules as {@link Boolean#valueOf(String)}.
		 */
		public boolean getBooleanValue() {
			return Boolean.parseBoolean(value);
		}

		/**
		 * Returns the integer value of this property, converting from string using the
		 * same rules as {@link Integer#parseInt(String)}.
		 * 
		 * @throws NumberFormatException if the value cannot be parsed as an integer.
		 */
		public int getIntValue() {
			return Integer.parseInt(value);
		}

		@Override
		public String toString() {
			return name + " = \"" + value + "\"" + (isDefault ? " (Defaulted)" : "");
		}
	}
}
