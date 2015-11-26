package org.eclipse.jgit.lfs.server;

import java.util.List;
import java.util.Map;

interface Response {
	class Header {
		String key;
		String value;
	}

	class Action {
		String href;
		Header header;
	}

	class ObjectInfo {
		String oid;
		long size;
		Map<String, Action> actions;
	}

	class Body {
		List<ObjectInfo> objects;
	}
}