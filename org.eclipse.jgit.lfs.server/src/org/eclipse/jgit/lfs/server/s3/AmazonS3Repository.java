package org.eclipse.jgit.lfs.server.s3;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SimpleTimeZone;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lfs.server.LargeFileRepository;
import org.eclipse.jgit.lfs.server.Response;
import org.eclipse.jgit.lfs.server.Response.Action;

/**
 * Signing Amazon S3 requests using Signature Version 4
 */
public class AmazonS3Repository implements LargeFileRepository {

	private static final String ALGORITHM = "HMAC-SHA256"; //$NON-NLS-1$

	private static final String DATE_STRING_FORMAT = "yyyyMMdd"; //$NON-NLS-1$

	private static final String HMACSHA256 = "HmacSHA256"; //$NON-NLS-1$

	private static final String ISO8601_BASIC_FORMAT = "yyyyMMdd'T'HHmmss'Z'"; //$NON-NLS-1$

	private static final String S3 = "s3"; //$NON-NLS-1$

	private static final String SCHEME = "AWS4"; //$NON-NLS-1$

	private static final String TERMINATOR = "aws4_request"; //$NON-NLS-1$

	private static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"; //$NON-NLS-1$

	private static final String UTC = "UTC"; //$NON-NLS-1$

	private static String getCanonicalizeHeaderNames(
			Map<String, String> headers) {
		List<String> sortedHeaders = new ArrayList<String>();
		sortedHeaders.addAll(headers.keySet());
		Collections.sort(sortedHeaders, String.CASE_INSENSITIVE_ORDER);

		StringBuilder buffer = new StringBuilder();
		for (String header : sortedHeaders) {
			if (buffer.length() > 0)
				buffer.append(";"); //$NON-NLS-1$
			buffer.append(header.toLowerCase());
		}

		return buffer.toString();
	}

	private static String getCanonicalizedHeaderString(
			Map<String, String> headers) {
		if (headers == null || headers.isEmpty()) {
			return ""; //$NON-NLS-1$
		}

		List<String> sortedHeaders = new ArrayList<String>();
		sortedHeaders.addAll(headers.keySet());
		Collections.sort(sortedHeaders, String.CASE_INSENSITIVE_ORDER);

		StringBuilder buffer = new StringBuilder();
		for (String key : sortedHeaders) {
			buffer.append(key.toLowerCase().replaceAll("\\s+", " ") + ":" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					+ headers.get(key).replaceAll("\\s+", " ")); //$NON-NLS-1$//$NON-NLS-2$
			buffer.append("\n"); //$NON-NLS-1$
		}

		return buffer.toString();
	}

	private static String getCanonicalizedQueryString(
			Map<String, String> parameters) {
		if (parameters == null || parameters.isEmpty()) {
			return ""; //$NON-NLS-1$
		}

		SortedMap<String, String> sorted = new TreeMap<String, String>();

		Iterator<Map.Entry<String, String>> pairs = parameters.entrySet()
				.iterator();
		while (pairs.hasNext()) {
			Map.Entry<String, String> pair = pairs.next();
			String key = pair.getKey();
			String value = pair.getValue();
			sorted.put(urlEncode(key, false), urlEncode(value, false));
		}

		StringBuilder builder = new StringBuilder();
		pairs = sorted.entrySet().iterator();
		while (pairs.hasNext()) {
			Map.Entry<String, String> pair = pairs.next();
			builder.append(pair.getKey());
			builder.append("="); //$NON-NLS-1$
			builder.append(pair.getValue());
			if (pairs.hasNext()) {
				builder.append("&"); //$NON-NLS-1$
			}
		}

		return builder.toString();
	}

	private static String getCanonicalRequest(URL endpoint, String httpMethod,
			String queryParameters, String canonicalizedHeaderNames,
			String canonicalizedHeaders, String bodyHash) {
		return String.format("%s\n%s\n%s\n%s\n%s\n%s", //$NON-NLS-1$
				httpMethod, getCanonicalizedResourcePath(endpoint),
				queryParameters, canonicalizedHeaders, canonicalizedHeaderNames,
				bodyHash);
	}

	private static String getCanonicalizedResourcePath(URL endpoint) {
		if (endpoint == null) {
			return "/"; //$NON-NLS-1$
		}
		String path = endpoint.getPath();
		if (path == null || path.isEmpty()) {
			return "/"; //$NON-NLS-1$
		}

		String encodedPath = urlEncode(path, true);
		if (encodedPath.startsWith("/")) { //$NON-NLS-1$
			return encodedPath;
		} else {
			return "/" + encodedPath; //$NON-NLS-1$
		}
	}

	private static byte[] hash(String s) {
		MessageDigest md = Constants.newMessageDigest();
		md.update(s.getBytes(StandardCharsets.UTF_8));
		return md.digest();
	}

	private static byte[] sign(String stringData, byte[] key) {
		try {
			byte[] data = stringData.getBytes("UTF-8"); //$NON-NLS-1$
			Mac mac = Mac.getInstance(HMACSHA256);
			mac.init(new SecretKeySpec(key, HMACSHA256));
			return mac.doFinal(data);
		} catch (Exception e) {
			throw new RuntimeException(
					"Unable to calculate a request signature: "
							+ e.getMessage(),
					e);
		}
	}

	private static final String HEX = "0123456789abcdef"; //$NON-NLS-1$

	private static String getStringToSign(String scheme, String algorithm,
			String dateTime, String scope, String canonicalRequest) {
		return String.format("%s-%s\n%s\n%s\n%s", //$NON-NLS-1$
				scheme, algorithm, dateTime, scope,
				toHex(hash(canonicalRequest)));
	}

	private static String toHex(byte[] bytes) {
		StringBuilder builder = new StringBuilder(2 * bytes.length);
		for (byte b : bytes) {
			builder.append(HEX.charAt((b & 0xF0) >> 4));
			builder.append(HEX.charAt(b & 0xF));
		}
		return builder.toString();
	}

	private static String urlEncode(String url, boolean keepPathSlash) {
		String encoded;
		try {
			encoded = URLEncoder.encode(url, StandardCharsets.UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("UTF-8 encoding is not supported.", e);
		}
		if (keepPathSlash) {
			encoded = encoded.replace("%2F", "/"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return encoded;
	}

	private String region;

	private String bucket;

	private int expirationSeconds;

	private String storageClass;

	private final String accessKey;

	private final String secretKey;


	/**
	 * @param region
	 * @param bucket
	 * @param expirationSeconds
	 * @param storageClass
	 * @param accessKey
	 * @param secretKey
	 */
	public AmazonS3Repository(String region, String bucket,
			int expirationSeconds, String storageClass,
			String accessKey, String secretKey) {
		this.region = region;
		this.bucket = bucket;
		this.expirationSeconds = expirationSeconds;
		this.storageClass = storageClass;
		this.accessKey = accessKey;
		this.secretKey = secretKey;
	}

	@Override
	public Response.Action getDownloadAction(AnyLongObjectId oid) {
		URL endpointUrl = getObjectUrl(region, bucket, getPath(oid));

		// construct the query parameter string to accompany the url
		Map<String, String> queryParams = new HashMap<String, String>();
		queryParams.put("X-Amz-Expires", Integer.toString(expirationSeconds)); //$NON-NLS-1$

		// we have no headers for this sample, but the signer will add 'host'
		Map<String, String> headers = new HashMap<String, String>();

		String authorizationQueryParameters = computeSignature(endpointUrl,
				"GET", //$NON-NLS-1$
				region, headers, queryParams, UNSIGNED_PAYLOAD);

		Response.Action a = new Response.Action();
		a.href = endpointUrl.toString() + "?" + authorizationQueryParameters; //$NON-NLS-1$
		return a;
	}

	@Override
	public Response.Action getUploadAction(AnyLongObjectId oid, long size) {
		// URL endpointUrl = getObjectUrl(region, bucket, getPath(oid));
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("x-amz-content-sha256", oid.getName()); //$NON-NLS-1$
		headers.put("content-length", Long.toString(size)); //$NON-NLS-1$
		headers.put("x-amz-storage-class", storageClass); //$NON-NLS-1$
		return null;
	}

	private String getPath(AnyLongObjectId oid) {
		return oid.getName();
	}

	private URL getObjectUrl(String region, String bucket, String path) {
		URL endpointUrl;
		try {
			endpointUrl = new URL(String.format(
					"https://s3-%s.amazonaws.com/%s/%s", region, bucket, path)); //$NON-NLS-1$
		} catch (MalformedURLException e) {
			throw new RuntimeException(
					"Unable to parse service endpoint: " + e.getMessage());
		}
		return endpointUrl;
	}

	/**
	 * Computes an AWS4 authorization for a request, suitable for embedding in
	 * query parameters.
	 *
	 * @param objectUrl
	 * @param httpMethod
	 * @param region
	 *
	 * @param headers
	 *            The request headers; 'Host' and 'X-Amz-Date' will be added to
	 *            this set.
	 * @param queryParameters
	 *            Any query parameters that will be added to the endpoint. The
	 *            parameters should be specified in canonical format.
	 * @param bodyHash
	 *            Precomputed SHA256 hash of the request body content; this
	 *            value should also be set as the header 'X-Amz-Content-SHA256'
	 *            for non-streaming uploads.
	 * @return The computed authorization string for the request. This value
	 *         needs to be set as the header 'Authorization' on the subsequent
	 *         HTTP request.
	 */
	private String computeSignature(URL objectUrl, String httpMethod,
			String region, Map<String, String> headers,
			Map<String, String> queryParameters, String bodyHash) {

		SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
				ISO8601_BASIC_FORMAT);
		dateTimeFormat.setTimeZone(new SimpleTimeZone(0, UTC));
		Date now = new Date();
		String dateTimeStamp = dateTimeFormat.format(now);

		// make sure "Host" header is added
		StringBuilder h = new StringBuilder(objectUrl.getHost());
		int port = objectUrl.getPort();
		if (port > -1) {
			h.append(":").append(Integer.toString(port)); //$NON-NLS-1$
		}
		headers.put("Host", h.toString()); //$NON-NLS-1$

		// canonicalized headers need to be expressed in the query
		// parameters processed in the signature
		String canonicalizedHeaderNames = getCanonicalizeHeaderNames(headers);
		String canonicalizedHeaders = getCanonicalizedHeaderString(headers);

		// we need scope as part of the query parameters
		SimpleDateFormat dateStampFormat = new SimpleDateFormat(
				DATE_STRING_FORMAT);
		dateStampFormat.setTimeZone(new SimpleTimeZone(0, UTC));
		String dateStamp = dateStampFormat.format(now);
		String scope = String.format("%s/%s/%s/%s", dateStamp, region, S3, //$NON-NLS-1$
				TERMINATOR);

		// add the fixed authorization params required by Signature V4
		queryParameters.put("X-Amz-Algorithm", SCHEME + "-" + ALGORITHM); //$NON-NLS-1$ //$NON-NLS-2$
		queryParameters.put("X-Amz-Credential", accessKey + "/" + scope); //$NON-NLS-1$ //$NON-NLS-2$

		// x-amz-date is now added as a query parameter, but still need to be in
		// ISO8601 basic form
		queryParameters.put("X-Amz-Date", dateTimeStamp); //$NON-NLS-1$
		queryParameters.put("X-Amz-SignedHeaders", canonicalizedHeaderNames); //$NON-NLS-1$

		// build the expanded canonical query parameter string that will go into
		// the signature computation
		String canonicalizedQueryParameters = getCanonicalizedQueryString(
				queryParameters);

		// express all the header and query parameter data as a canonical
		// request string
		String canonicalRequest = getCanonicalRequest(objectUrl, httpMethod,
				canonicalizedQueryParameters, canonicalizedHeaderNames,
				canonicalizedHeaders, bodyHash);

		// construct the string to be signed
		String stringToSign = getStringToSign(SCHEME, ALGORITHM, dateTimeStamp,
				scope, canonicalRequest);

		// compute the signing key
		byte[] kSecret = (SCHEME + secretKey).getBytes();
		byte[] kDate = sign(dateStamp, kSecret);
		byte[] kRegion = sign(region, kDate);
		byte[] kService = sign(S3, kRegion);
		byte[] kSigning = sign(TERMINATOR, kService);
		byte[] signature = sign(stringToSign, kSigning);

		// form up the authorization parameters for the caller to place in the
		// query string
		StringBuilder s = new StringBuilder();
		s.append("X-Amz-Algorithm=" + queryParameters.get("X-Amz-Algorithm")); //$NON-NLS-1$ //$NON-NLS-2$
		s.append("&X-Amz-Credential=" + queryParameters.get("X-Amz-Credential")); //$NON-NLS-1$ //$NON-NLS-2$
		s.append("&X-Amz-Date=" + queryParameters.get("X-Amz-Date")); //$NON-NLS-1$ //$NON-NLS-2$
		s.append("&X-Amz-Expires=" + queryParameters.get("X-Amz-Expires")); //$NON-NLS-1$//$NON-NLS-2$
		s.append("&X-Amz-SignedHeaders=" + queryParameters.get("X-Amz-SignedHeaders")); //$NON-NLS-1$ //$NON-NLS-2$
		s.append("&X-Amz-Signature=" + toHex(signature)); //$NON-NLS-1$
		return s.toString();
	}

	@Override
	public Action getVerifyAction(AnyLongObjectId id) {
		return null;
	}

	@Override
	public long getSize(AnyLongObjectId id) throws IOException {
		return 0;
	}
}
