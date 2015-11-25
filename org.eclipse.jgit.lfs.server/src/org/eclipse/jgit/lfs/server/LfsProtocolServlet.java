package org.eclipse.jgit.lfs.server;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * @author d038025
 *
 */
public class LfsProtocolServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static class LfsObject {
		String oid;
		long size;
	}

	private static class LfsRequest {
		String operation;
		List<LfsObject> objects;
	}

	private static class Header {
		String key;
		String value;
	}

	private static class Action {
		String href;
		Header header;
	}

	private static class LfsObjectInfo {
		String oid;
		long size;
		Map<String, Action> actions;
	}

	private static class ResponseBody {
		List<LfsObjectInfo> objects;
	}

	private final String objectStoreUrl;

	/**
	 * @param objectStoreUrl
	 */
	public LfsProtocolServlet(String objectStoreUrl) {
		this.objectStoreUrl = objectStoreUrl;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse res)
			throws ServletException, IOException {
		res.setStatus(SC_OK);
		res.setContentType("application/vnd.git-lfs+json");

		Writer w = new BufferedWriter(
				new OutputStreamWriter(res.getOutputStream(), UTF_8));

		GsonBuilder gb = new GsonBuilder()
				.setFieldNamingPolicy(
						FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
				.setPrettyPrinting();

		Gson gson = gb.create();
		Reader r = new BufferedReader(new InputStreamReader(req.getInputStream(), UTF_8));
		LfsRequest request = gson.fromJson(r, LfsRequest.class);

		ResponseBody body = new ResponseBody();
		if (request.objects.size() > 0) {
			body.objects = new ArrayList<>();
			for (LfsObject o : request.objects) {
				LfsObjectInfo info = new LfsObjectInfo();
				body.objects.add(info);
				info.oid = o.oid;
				info.size = o.size;
				info.actions = new HashMap<>();
				Action a = new Action();
				info.actions.put(request.operation, a);
				a.href = objectStoreUrl;
				a.header = new Header();
				a.header.key = "Key";
				a.header.value = "value";
			}
		}

		gson.toJson(body, w);

		w.flush();
	}
}
