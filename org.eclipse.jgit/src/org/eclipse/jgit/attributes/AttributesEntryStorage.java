package org.eclipse.jgit.attributes;

import java.util.*;

/**
 * @author Marc Strapetz
 */
class AttributesEntryStorage {

	private static final Comparator<AttributesEntry> ENTRY_BY_PRIORITY_COMPARATOR = new Comparator<AttributesEntry>() {
		public int compare(AttributesEntry o1, AttributesEntry o2) {
			return o1.priority < o2.priority ? -1 : (o1.priority > o2.priority ? +1 : 0);
		}
	};

	private final List<Object> items = new ArrayList<Object>();
	private final List<AttributesEntry> matcherPatterns = new ArrayList<AttributesEntry>();
	private final Map<String, Object> pathToSimplePattern = new HashMap<String, Object>();

	public final List<String> getPatterns() {
		final List<String> patterns = new ArrayList<String>();
		for (Object item : items) {
			if (!(item instanceof AttributesEntry)) {
				continue;
			}

			final AttributesEntry entry = (AttributesEntry)item;
			patterns.add(entry.pattern);
		}

		return patterns;
	}

	public boolean isEmpty() {
		return items.size() == 0;
	}

	public List<Object> getItems() {
		return items;
	}

	public void addLine(String line) {
		items.add(line);
	}

	public void addEntry(AttributesEntry entry) {
		items.add(entry);
		entry.priority = items.size() - 1;
		if (entry.matcher.isSimple()) {
			final String path = normalizePath(entry.pattern);
			final Object obj = pathToSimplePattern.get(path);
			if (obj == null) {
				pathToSimplePattern.put(path, entry);
			}
			else if (obj instanceof AttributesEntry) {
				pathToSimplePattern.put(path, new ArrayList<AttributesEntry>(Arrays.asList((AttributesEntry)obj, entry)));
			}
			else if (obj instanceof List) {
				((List)obj).add(entry);
			}
			else {
				throw new RuntimeException();
			}
		}
		else {
			matcherPatterns.add(entry);
		}
	}

	public List<AttributesEntry> getPatterns(String relativePath) {
		final Object simpleObj = pathToSimplePattern.get(normalizePath(relativePath));
		if (simpleObj == null) {
			return matcherPatterns;
		}

		final List<AttributesEntry> entries = new ArrayList<AttributesEntry>(matcherPatterns);
		if (simpleObj instanceof AttributesEntry) {
			insertEntryIntoList((AttributesEntry)simpleObj, entries);
		}
		else if (simpleObj instanceof List) {
			for (AttributesEntry entry : ((List<AttributesEntry>)simpleObj)) {
				insertEntryIntoList(entry, entries);
			}
		}
		else {
			throw new RuntimeException();
		}

		return entries;
	}

	public void clear() {
		items.clear();
		matcherPatterns.clear();
		pathToSimplePattern.clear();
	}

	private static void insertEntryIntoList(AttributesEntry simple, List<AttributesEntry> entries) {
		final int insertionPoint = Collections.binarySearch(entries, simple, ENTRY_BY_PRIORITY_COMPARATOR);
		if (insertionPoint >= 0) {
			entries.add(insertionPoint, simple);
		}
		else {
			entries.add(-(insertionPoint + 1), simple);
		}
	}

	private static String normalizePath(String path) {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		if (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		return path;
	}
}