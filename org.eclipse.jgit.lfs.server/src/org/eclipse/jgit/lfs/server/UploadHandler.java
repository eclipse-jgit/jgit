package org.eclipse.jgit.lfs.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lfs.lib.LargeFileRepository;
import org.eclipse.jgit.lfs.lib.LongObjectId;

class UploadHandler {

	private static final String DOWNLOAD = "download"; //$NON-NLS-1$
	private static final String UPLOAD = "upload"; //$NON-NLS-1$

	private final LargeFileRepository repository;
	private final List<LfsObject> objects;

	UploadHandler(LargeFileRepository repository, List<LfsObject> objects) {
		this.repository = repository;
		this.objects = objects;
	}

	Response.Body process() {
		Response.Body body = new Response.Body();
		if (objects.size() > 0) {
			body.objects = new ArrayList<>();
			for (LfsObject o : objects) {
				Response.ObjectInfo info = new Response.ObjectInfo();
				body.objects.add(info);
				info.oid = o.oid;
				info.size = o.size;
				info.actions = new HashMap<>();

				LongObjectId oid = LongObjectId.fromString(o.oid);
				addAction(UPLOAD, oid, info.actions);
				if (repository.exists(oid)) {
					addAction(DOWNLOAD, oid, info.actions);
				}
			}
		}
		return body;
	}

	private void addAction(String name, LongObjectId oid,
			Map<String, Response.Action> actions) {
		Response.Action action = new Response.Action();
		action.href = repository.getUrl() + oid.getName();
		action.header = new Response.Header();
		// TODO: when should this be used:
		action.header.key = "Key";
		action.header.value = "value";
		actions.put(name, action);
	}
}
