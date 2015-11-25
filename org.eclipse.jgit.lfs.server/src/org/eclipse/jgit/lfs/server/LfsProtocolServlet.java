package org.eclipse.jgit.lfs.server;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
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

		ResponseBody body = new ResponseBody();
		body.objects = new ArrayList<>();
		LfsObjectInfo info = new LfsObjectInfo();
		body.objects.add(info);
		info.oid = "111111";
		info.size = 123;
		info.actions = new HashMap<>();
		Action a = new Action();
		info.actions.put("upload", a);
		a.href = "https://localhost:8081/objects";
		a.header = new Header();
		a.header.key = "Key";
		a.header.value = "value";

		a = new Action();
		info.actions.put("verify", a);
		a.href = "https://localhost:8081/objects";
		a.header = new Header();
		a.header.key = "Key";
		a.header.value = "value";

		Gson gson = gb.create();
		gson.toJson(body, w);

		w.flush();
	}
}
