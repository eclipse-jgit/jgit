package org.eclipse.jgit.lfs.server.internal;

import java.io.Reader;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

/**
 * Wrapper for {@link Gson} used by LFS servlets.
 *
 */
public class LfsGson {
	private static final Gson gson = new GsonBuilder()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.disableHtmlEscaping()
			.create();

	/**
	 * Wrapper class only used for serialization of error messages.
	 */
	static class Error {
		String message;

		Error(String m) {
			this.message = m;
		}
	}

	/**
	 * Serializes the specified object into its equivalent Json representation.
	 *
	 * @param src
	 *            the object for which Json representation is to be created. If
	 *            this is a String, it is wrapped in an instance of
	 *            {@link Error}.
	 * @param writer
	 *            Writer to which the Json representation needs to be written
	 * @throws JsonIOException
	 *             if there was a problem writing to the writer
	 * @see Gson#toJson(Object, Appendable)
	 */
	public static void toJson(Object src, Appendable writer)
			throws JsonIOException {
		if (src instanceof String) {
			gson.toJson(new Error((String) src), writer);
		} else {
			gson.toJson(src, writer);
		}
	}

	/**
	 * Deserializes the Json read from the specified reader into an object of
	 * the specified type.
	 *
	 * @param json
	 *            reader producing json from which the object is to be
	 *            deserialized
	 * @param classOfT
	 *            specified type to deserialize
	 * @return an Object of type T
	 * @throws JsonIOException
	 *             if there was a problem reading from the Reader
	 * @throws JsonSyntaxException
	 *             if json is not a valid representation for an object of type
	 * @see Gson#fromJson(Reader, java.lang.reflect.Type)
	 */
	public static <T> T fromJson(Reader json, Class<T> classOfT)
			throws JsonSyntaxException, JsonIOException {
		return gson.fromJson(json, classOfT);
	}
}
