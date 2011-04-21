package org.eclipse.jgit.junit.http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.eclipse.jgit.util.Base64;

public class KeyStoreHelper {

	// server keystore (JKS, manually Base64-encoded)
	private static final String SERVER_KEYSTORE = "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";

	// server certificate (X.509, manually Base64-encoded)
	private static final String SERVER_X509 = "MIICTTCCAbagAwIBAgIETaavCjANBgkqhkiG9w0BAQUFADBrMRAwDgYDVQQGEwdVbmtub3duMRAw"
			+ "DgYDVQQIEwdVbmtub3duMRAwDgYDVQQHEwdVbmtub3duMRAwDgYDVQQKEwdVbmtub3duMRAwDgYD"
			+ "VQQLEwdVbmtub3duMQ8wDQYDVQQDEwZzZXJ2ZXIwHhcNMTEwNDE0MDgyMzM4WhcNMjEwNDExMDgy"
			+ "MzM4WjBrMRAwDgYDVQQGEwdVbmtub3duMRAwDgYDVQQIEwdVbmtub3duMRAwDgYDVQQHEwdVbmtu"
			+ "b3duMRAwDgYDVQQKEwdVbmtub3duMRAwDgYDVQQLEwdVbmtub3duMQ8wDQYDVQQDEwZzZXJ2ZXIw"
			+ "gZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAI7S6h7fExjwDei8eT4OnW1i1OC612wCF7y3S8+E"
			+ "FTS2tO5Af/1ZSE5EAX29/tpUPjGlDZqMyYbW5EhdHI6iakQONpW+KKRLPlBL0jhjfOTwGktv6/1W"
			+ "/uUPgf8V9ORKmXAvlEnpJlhTnkz5wvH5qc4AAvuGIfnv+6bVQ3ixmQ7rAgMBAAEwDQYJKoZIhvcN"
			+ "AQEFBQADgYEAVF3Zjzj6LJo+k7kZSF/gjFhdUOwJhd8drEf4uZtDWY1/iockvemhQQUk918aCxje"
			+ "qmxX6vNJIzpq+j1BGVnMgTicJ34vFG5qXHnWaaz6mt8RqSWZ9P+n2x7EOcvpV36u7gCMuZNwf7jj"
			+ "zFkuY8OVZxV8jxml0iv0ji16sIjkcvc=";

	// client key (PKCS #12, manually Base64-encoded)
	private static final String CLIENT_PKCS12 = "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";

	// client key (JKS, manually Base64-encoded)
	private static final String CLIENT_JKS = "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";

	private static File serverKeystore = null;

	private static File serverX509 = null;

	private static File clientPKCS12 = null;

	private static File clientJKS = null;

	public static String pathToServerKeyStore() {
		try {
			if (serverKeystore == null) {
				serverKeystore = File.createTempFile("server_keystore", "jks");
				final FileOutputStream fos = new FileOutputStream(
						serverKeystore);
				byte[] bytes = Base64.decode(SERVER_KEYSTORE);
				fos.write(bytes);
				fos.close();
			}
			return serverKeystore.getAbsolutePath();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String serverKeyStorePassword() {
		return "server";
	}

	public static String pathToSslCAInfo() {
		try {
			if (serverX509 == null) {
				serverX509 = File.createTempFile("ssl_ca_info",
						"cer");
				final FileOutputStream fos = new FileOutputStream(
serverX509);
				byte[] bytes = Base64.decode(SERVER_X509);
				fos.write(bytes);
				fos.close();
			}
			return serverX509.getAbsolutePath();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String pathToSslKeyPKCS12() {
		try {
			if (clientPKCS12 == null) {
				clientPKCS12 = File.createTempFile("ssl_key", "p12");
				final FileOutputStream fos = new FileOutputStream(clientPKCS12);
				byte[] bytes = Base64.decode(CLIENT_PKCS12);
				fos.write(bytes);
				fos.close();
			}
			return clientPKCS12.getAbsolutePath();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String pathToSslKeyJKS() {
		try {
			if (clientJKS == null) {
				clientJKS = File.createTempFile("ssl_key", "jks");
				final FileOutputStream fos = new FileOutputStream(clientJKS);
				byte[] bytes = Base64.decode(CLIENT_JKS);
				fos.write(bytes);
				fos.close();
			}
			return clientJKS.getAbsolutePath();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String sslKeyPassword() {
		return "client";
	}

	public static void cleanUp() {
		if (serverKeystore != null)
			serverKeystore.delete();
		if (serverX509 != null)
			serverX509.delete();
		if (clientPKCS12 != null)
			clientPKCS12.delete();
		if (clientJKS != null)
			clientJKS.delete();
	}
}
