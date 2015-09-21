/*
 * Copyright (C) 2015, Andrei Pozolotin.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.transport;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.crypto.SecretKeyFactory;

import org.apache.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

import static org.junit.Assume.*;

/**
 * Amazon S3 encryption pipeline test.
 *
 * See {@link AmazonS3} {@link WalkEncryption}
 *
 * Note: CI server must provide amazon credentials (access key, secret key,
 * bucket name) via one of methods available in
 * {@link WalkEncryptionTest.TestKeys}.
 *
 * Note: long running tests are activated by maven profile "test.long". There is
 * also a separate eclipse m2e launcher for that. See 'pom.xml' and
 * 'WalkEncryptionTest.launch'.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class WalkEncryptionTest extends SampleDataRepositoryTestCase {

	/**
	 * Logger setup: ${project_loc}/tst-rsrc/log4j.properties
	 */
	static final Logger logger = Logger.getLogger(WalkEncryptionTest.class);

	/**
	 * Property names used in test session.
	 */
	interface TestKeys {

		// Names of discovered test properties.

		String TEST_BUCKET = "test.bucket";

		// Names of test environment variables for CI.

		String ENV_ACCESS_KEY = "JGIT_S3_ACCESS_KEY";

		String ENV_SECRET_KEY = "JGIT_S3_SECRET_KEY";

		String ENV_BUCKET_NAME = "JGIT_S3_BUCKET_NAME";

		// Name of test environment variable file path for CI.

		String ENV_CONFIG_FILE = "JGIT_S3_CONFIG_FILE";

		// Names of test system properties for CI.

		String SYS_ACCESS_KEY = "jgit.s3.access.key";

		String SYS_SECRET_KEY = "jgit.s3.secret.key";

		String SYS_BUCKET_NAME = "jgit.s3.bucket.name";

		// Name of test system property file path for CI.
		String SYS_CONFIG_FILE = "jgit.s3.config.file";

		// Hard coded name of test properties file for CI.
		// File format follows AmazonS3.Keys:
		// #
		// # Required entries:
		// #
		// accesskey = your-amazon-access-key # default AmazonS3.Keys
		// secretkey = your-amazon-secret-key # default AmazonS3.Keys
		// test.bucket = your-bucket-for-testing # custom keys, for this test
		String CONFIG_FILE = "jgit-s3-config.properties";

		// Hard test properties file in user home of CI.
		String HOME_CONFIG_FILE = System.getProperty("user.home")
				+ File.separator + CONFIG_FILE;

		// Hard test properties file in project work directory of CI.
		String WORK_CONFIG_FILE = System.getProperty("user.dir")
				+ File.separator + CONFIG_FILE;

		// Hard test properties file in project test source directory of CI.
		String TEST_CONFIG_FILE = System.getProperty("user.dir")
				+ File.separator + "tst-rsrc" + File.separator + CONFIG_FILE;

	}

	/**
	 * Find test properties from various sources in order of priority.
	 */
	static class TestProps implements TestKeys, AmazonS3.Keys {

		static boolean haveEnvVar(String name) {
			return System.getenv(name) != null;
		}

		static boolean haveEnvVarFile(String name) {
			return haveEnvVar(name) && new File(name).exists();
		}

		static boolean haveSysProp(String name) {
			return System.getProperty(name) != null;
		}

		static boolean haveSysPropFile(String name) {
			return haveSysProp(name) && new File(name).exists();
		}

		static void loadEnvVar(String source, String target, Properties props) {
			props.put(target, System.getenv(source));
		}

		static void loadSysProp(String source, String target,
				Properties props) {
			props.put(target, System.getProperty(source));
		}

		static boolean haveProp(String name, Properties props) {
			return props.containsKey(name);
		}

		static boolean checkTestProps(Properties props) {
			return haveProp(ACCESS_KEY, props) && haveProp(SECRET_KEY, props)
					&& haveProp(TEST_BUCKET, props);
		}

		static Properties fromEnvVars() {
			if (haveEnvVar(ENV_ACCESS_KEY) && haveEnvVar(ENV_SECRET_KEY)
					&& haveEnvVar(ENV_BUCKET_NAME)) {
				Properties props = new Properties();
				loadEnvVar(ENV_ACCESS_KEY, ACCESS_KEY, props);
				loadEnvVar(ENV_SECRET_KEY, SECRET_KEY, props);
				loadEnvVar(ENV_BUCKET_NAME, TEST_BUCKET, props);
				return props;
			} else {
				return null;
			}
		}

		static Properties fromEnvFile() throws Exception {
			if (haveEnvVarFile(ENV_CONFIG_FILE)) {
				Properties props = new Properties();
				props.load(new FileInputStream(ENV_CONFIG_FILE));
				if (checkTestProps(props)) {
					return props;
				} else {
					throw new Error("Environment config file is incomplete.");
				}
			} else {
				return null;
			}
		}

		static Properties fromSysProps() {
			if (haveSysProp(SYS_ACCESS_KEY) && haveSysProp(SYS_SECRET_KEY)
					&& haveSysProp(SYS_BUCKET_NAME)) {
				Properties props = new Properties();
				loadSysProp(SYS_ACCESS_KEY, ACCESS_KEY, props);
				loadSysProp(SYS_SECRET_KEY, SECRET_KEY, props);
				loadSysProp(SYS_BUCKET_NAME, TEST_BUCKET, props);
				return props;
			} else {
				return null;
			}
		}

		static Properties fromSysFile() throws Exception {
			if (haveSysPropFile(SYS_CONFIG_FILE)) {
				Properties props = new Properties();
				props.load(new FileInputStream(SYS_CONFIG_FILE));
				if (checkTestProps(props)) {
					return props;
				} else {
					throw new Error("System props config file is incomplete.");
				}
			} else {
				return null;
			}
		}

		static Properties fromConfigFile(String path) throws Exception {
			File file = new File(path);
			if (file.exists()) {
				Properties props = new Properties();
				props.load(new FileInputStream(file));
				if (checkTestProps(props)) {
					return props;
				} else {
					throw new Error("Props config file is incomplete: " + path);
				}
			} else {
				return null;
			}
		}

		/**
		 * Find test properties from various sources in order of priority.
		 *
		 * @return result
		 * @throws Exception
		 */
		static Properties discover() throws Exception {
			Properties props;
			if ((props = fromEnvVars()) != null) {
				logger.debug(
						"Using test properties from environment variables.");
				return props;
			}
			if ((props = fromEnvFile()) != null) {
				logger.debug(
						"Using test properties from environment variable config file.");
				return props;
			}
			if ((props = fromSysProps()) != null) {
				logger.debug("Using test properties from system properties.");
				return props;
			}
			if ((props = fromSysFile()) != null) {
				logger.debug(
						"Using test properties from system property config file.");
				return props;
			}
			if ((props = fromConfigFile(HOME_CONFIG_FILE)) != null) {
				logger.debug(
						"Using test properties from hard coded ${user.home} file.");
				return props;
			}
			if ((props = fromConfigFile(WORK_CONFIG_FILE)) != null) {
				logger.debug(
						"Using test properties from hard coded ${user.dir} file.");
				return props;
			}
			if ((props = fromConfigFile(TEST_CONFIG_FILE)) != null) {
				logger.debug(
						"Using test properties from hard coded ${user.dir} file.");
				return props;
			}
			throw new Error("Can not load test properteis form any source.");
		}

	}

	static final Charset UTF_8 = Charset.forName("UTF-8");

	static String textRead(File file) throws Exception {
		return new String(Files.readAllBytes(file.toPath()), UTF_8);
	}

	static void textWrite(File file, String text) throws Exception {
		Files.write(file.toPath(), text.getBytes(UTF_8));
	}

	static void verifyFileContent(File fileOne, File fileTwo) throws Exception {
		assertTrue(fileOne.length() > 0);
		assertTrue(fileTwo.length() > 0);
		String textOne = textRead(fileOne);
		String textTwo = textRead(fileTwo);
		assertEquals(textOne, textTwo);
	}

	/**
	 * S3 URI user used by JGIT to discover connection configuration file.
	 */
	static final String JGIT_USER = "tester-" + System.currentTimeMillis();

	/**
	 * S3 content encoding password used for this test session.
	 */
	static final String JGIT_PASS = "secret-" + System.currentTimeMillis();

	/**
	 * S3 repository configuration file expected by {@link AmazonS3}.
	 */
	static final String JGIT_CONF_FILE = System.getProperty("user.home") + "/"
			+ JGIT_USER;

	/**
	 * Name representing remote or local JGIT repository.
	 */
	static final String JGIT_REPO_DIR = JGIT_USER + ".jgit";

	/**
	 * Local JGIT repository for this test session.
	 */
	static final String JGIT_LOCAL_DIR = System.getProperty("user.dir")
			+ "/target/" + JGIT_REPO_DIR;

	/**
	 * Remote JGIT repository for this test session.
	 */
	static final String JGIT_REMOTE_DIR = JGIT_REPO_DIR;

	/**
	 * Generate JGIT S3 connection configuration file.
	 *
	 * @param algorithm
	 * @throws Exception
	 */
	static void configCreate(String algorithm) throws Exception {
		Properties props = TestProps.discover();
		props.put(AmazonS3.Keys.PASSWORD, JGIT_PASS);
		props.put(AmazonS3.Keys.CRYPTO_ALG, algorithm);
		PrintWriter writer = new PrintWriter(JGIT_CONF_FILE);
		props.store(writer, "JGIT S3 connection configuration file.");
		writer.close();
	}

	/**
	 * Remove JGIT connection configuration file.
	 *
	 * @throws Exception
	 */
	static void configDelete() throws Exception {
		File path = new File(JGIT_CONF_FILE);
		FileUtils.delete(path, FileUtils.SKIP_MISSING);
	}

	/**
	 * Generate remote URI for the test session.
	 *
	 * @return result
	 * @throws Exception
	 */
	static String amazonURI() throws Exception {
		Properties props = TestProps.discover();
		String bucket = props.getProperty(TestKeys.TEST_BUCKET);
		assertNotNull(bucket);
		return TransportAmazonS3.S3_SCHEME + "://" + JGIT_USER + "@" + bucket
				+ "/" + JGIT_REPO_DIR;
	}

	/**
	 * Create local folder.
	 *
	 * @param folder
	 * @throws Exception
	 */
	static void folderCreate(String folder) throws Exception {
		File path = new File(folder);
		assertTrue(path.mkdirs());
	}

	/**
	 * Delete local folder.
	 *
	 * @param folder
	 * @throws Exception
	 */
	static void folderDelete(String folder) throws Exception {
		File path = new File(folder);
		FileUtils.delete(path, FileUtils.RECURSIVE | FileUtils.SKIP_MISSING);
	}

	/**
	 * Create S3 repository folder.
	 *
	 * @throws Exception
	 */
	static void remoteCreate() throws Exception {
		Properties props = TestProps.discover();
		props.remove(AmazonS3.Keys.PASSWORD); // Disable encryption.
		String bucket = props.getProperty(TestKeys.TEST_BUCKET);
		AmazonS3 s3 = new AmazonS3(props);
		String path = JGIT_REMOTE_DIR + "/";
		s3.put(bucket, path, new byte[0]);
		logger.debug("remote create: " + JGIT_REMOTE_DIR);
	}

	/**
	 * Delete S3 repository folder.
	 *
	 * @throws Exception
	 */
	static void remoteDelete() throws Exception {
		Properties props = TestProps.discover();
		props.remove(AmazonS3.Keys.PASSWORD); // Disable encryption.
		String bucket = props.getProperty(TestKeys.TEST_BUCKET);
		AmazonS3 s3 = new AmazonS3(props);
		List<String> list = s3.list(bucket, JGIT_REMOTE_DIR);
		for (String path : list) {
			path = JGIT_REMOTE_DIR + "/" + path;
			s3.delete(bucket, path);
		}
		logger.debug("remote delete: " + JGIT_REMOTE_DIR);
	}

	/**
	 * Discover Password-Based Encryption (PBE) engines providing both
	 * [SecretKeyFactory] and [AlgorithmParameters].
	 *
	 * @return result
	 */
	// https://www.bouncycastle.org/specifications.html
	// https://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html
	static List<String> cryptoCipherListPBE() {
		return cryptoCipherList("(PBE).*(WITH).+(AND).+");
	}

	static String securityProviderName(String algorithm) throws Exception {
		return SecretKeyFactory.getInstance(algorithm).getProvider().getName();
	}

	static List<String> cryptoCipherList(String regex) {
		Set<String> source = Security.getAlgorithms("Cipher");
		Set<String> target = new TreeSet<String>();
		for (String algo : source) {
			algo = algo.toUpperCase();
			if (algo.matches(regex)) {
				target.add(algo);
			}
		}
		return new ArrayList<String>(target);
	}

	/**
	 * Verify if any security provider published the algorithm.
	 *
	 * @param target
	 * @return result
	 */
	static boolean isAlgorithmPresent(String target) {
		Set<String> set = Security.getAlgorithms("Cipher");
		for (String source : set) {
			// Standard names are not case-sensitive.
			// http://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html
			if (source.equalsIgnoreCase(target)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Verify if we can perform tests
	 *
	 * @return result
	 */
	static boolean isTestConfigPresent() {
		try {
			TestProps.discover();
			return true;
		} catch (Throwable e) {
			return false;
		}
	}

	static void reportTestConfigPresent() {
		if (isTestConfigPresent()) {
			logger.info("Amazon S3 test configuration is present.");
		} else {
			logger.error(
					"Amazon S3 test configuration is missing, tests will not run.");
		}
	}

	/**
	 * Discover public address of CI.
	 *
	 * @return result
	 * @throws Exception
	 */
	static String publicAddress() throws Exception {
		String service = "http://checkip.amazonaws.com";
		URL url = new URL(service);
		BufferedReader in = new BufferedReader(
				new InputStreamReader(url.openStream()));
		try {
			return in.readLine();
		} finally {
			in.close();
		}
	}

	static void reportPublicAddress() throws Exception {
		logger.info("Public address: " + publicAddress());
	}

	/**
	 * BouncyCastle provider class.
	 *
	 * Needs extra dependency, see pom.xml
	 */
	// http://search.maven.org/#artifactdetails%7Corg.bouncycastle%7Cbcprov-jdk15on%7C1.52%7Cjar
	static final String PROVIDER_BC = "org.bouncycastle.jce.provider.BouncyCastleProvider";

	/**
	 * Load BouncyCastle provider if present.
	 */
	static void loadBouncyCastle() {
		try {
			Class<?> provider = Class.forName(PROVIDER_BC);
			Provider instance = (Provider) provider
					.getConstructor(new Class[] {})
					.newInstance(new Object[] {});
			Security.addProvider(instance);
			logger.info("Loaded " + PROVIDER_BC);
		} catch (Throwable e) {
			logger.warn("Failed to load " + PROVIDER_BC);
		}
	}

	/**
	 * Using maven profile activation, see pom.xml
	 *
	 * @return result
	 */
	static boolean permitLongTests() {
		return Boolean.parseBoolean(System.getProperty("jgit.test.long"));
	}

	static void reportLongTests() {
		if (permitLongTests()) {
			logger.info("Long running tests are enabled.");
		} else {
			logger.warn("Long running tests are disabled.");
		}
	}

	/**
	 * Setup proxy during CI.
	 *
	 * @throws Exception
	 */
	// https://wiki.eclipse.org/Hudson#Accessing_the_Internet_using_Proxy
	// http://docs.oracle.com/javase/7/docs/api/java/net/doc-files/net-properties.html
	static void proxySetup() throws Exception {
		String keyNoProxy = "no_proxy";
		String keyHttpProxy = "http_proxy";
		String keyHttpsProxy = "https_proxy";

		String no_proxy = System.getProperty(keyNoProxy,
				System.getenv(keyNoProxy));
		if (no_proxy != null) {
			System.setProperty("http.nonProxyHosts", no_proxy);
			logger.info("Proxy NOT: " + no_proxy);
		}

		String http_proxy = System.getProperty(keyHttpProxy,
				System.getenv(keyHttpProxy));
		if (http_proxy != null) {
			URL url = new URL(http_proxy);
			System.setProperty("http.proxyHost", url.getHost());
			System.setProperty("http.proxyPort", "" + url.getPort());
			logger.info("Proxy HTTP: " + http_proxy);
		}

		String https_proxy = System.getProperty(keyHttpsProxy,
				System.getenv(keyHttpsProxy));
		if (https_proxy != null) {
			URL url = new URL(https_proxy);
			System.setProperty("https.proxyHost", url.getHost());
			System.setProperty("https.proxyPort", "" + url.getPort());
			logger.info("Proxy HTTPS: " + https_proxy);
		}

		if (no_proxy == null && http_proxy == null && https_proxy == null) {
			logger.info("Proxy not used.");
		}

	}

	/**
	 * Non-PBE algorithm, for error check.
	 */
	static final String ALGO_ERROR = "PBKDF2WithHmacSHA1";

	/**
	 * Default JetS3t algorithm present in most JRE.
	 */
	static final String ALGO_JETS3T = "PBEWithMD5AndDES";

	/**
	 * Minimal strength AES based algorithm present in most JRE.
	 */
	static final String ALGO_MINIMAL_AES = "PBEWithHmacSHA1AndAES_128";

	/**
	 * Selected non-AES algorithm present in BouncyCastle provider.
	 */
	static final String ALGO_BOUNCY_CASTLE_CBC = "PBEWithSHAAndTwofish-CBC";

	//////////////////////////////////////////////////

	@BeforeClass
	public static void initialize() throws Exception {
		Transport.register(TransportAmazonS3.PROTO_S3);
		proxySetup();
		loadBouncyCastle();
		if (isTestConfigPresent()) {
			remoteCreate();
		}
		reportLongTests();
		reportPublicAddress();
		reportTestConfigPresent();
	}

	@AfterClass
	public static void terminate() throws Exception {
		configDelete();
		folderDelete(JGIT_LOCAL_DIR);
		if (isTestConfigPresent()) {
			remoteDelete();
		}
	}

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
	}

	@After
	@Override
	public void tearDown() throws Exception {
		super.tearDown();
	}

	@Test
	public void test_A1_ValidURI() throws Exception {
		assumeTrue(isTestConfigPresent());
		URIish uri = new URIish(amazonURI());
		assertTrue("uri=" + uri, TransportAmazonS3.PROTO_S3.canHandle(uri));
	}

	@Test(expected = Exception.class)
	public void test_A2_CryptoError() throws Exception {
		assumeTrue(isTestConfigPresent());
		cryptoTest(ALGO_ERROR);
	}

	@Test
	public void test_A3_CryptoJetS3tDefault() throws Exception {
		assumeTrue(isTestConfigPresent());
		cryptoTestIfAlgorithmIsPresent(ALGO_JETS3T);
	}

	@Test
	public void test_A4_CryptoMinimalAES() throws Exception {
		assumeTrue(isTestConfigPresent());
		cryptoTestIfAlgorithmIsPresent(ALGO_MINIMAL_AES);
	}

	@Test
	public void test_A5_CryptoBouncyCastleCBC() throws Exception {
		assumeTrue(isTestConfigPresent());
		cryptoTestIfAlgorithmIsPresent(ALGO_BOUNCY_CASTLE_CBC);
	}

	@Test // Can take long time, uses separate eclipse launcher.
	public void test_B1_CryptoAllPBE() throws Exception {
		assumeTrue(permitLongTests());
		assumeTrue(isTestConfigPresent());
		List<String> list = cryptoCipherListPBE();
		for (String algo : list) {
			try {
				setUp();
				cryptoTest(algo);
			} finally {
				tearDown();
			}
		}
	}

	/**
	 * Optional encrypted amazon remote JGIT life cycle test.
	 *
	 * @param algorithm
	 * @throws Exception
	 */
	void cryptoTestIfAlgorithmIsPresent(String algorithm) throws Exception {
		if (isAlgorithmPresent(algorithm)) {
			cryptoTest(algorithm);
		} else {
			logger.warn("Missing algorithm: " + algorithm);
		}
	}

	/**
	 * Required encrypted amazon remote JGIT life cycle test.
	 *
	 * @param algorithm
	 * @throws Exception
	 */
	void cryptoTest(String algorithm) throws Exception {

		logger.info("Testing algorithm: " + algorithm + " / "
				+ securityProviderName(algorithm));

		remoteDelete();
		configCreate(algorithm);
		folderDelete(JGIT_LOCAL_DIR);

		String uri = amazonURI();

		// Local repositories.
		File dirOne = db.getWorkTree(); // Provided by setup.
		File dirTwo = new File(JGIT_LOCAL_DIR);

		// Local verification files.
		String nameStatic = "master.txt"; // Provided by setup.
		String nameDynamic = JGIT_USER + "-" + UUID.randomUUID().toString();

		String remote = "remote";
		RefSpec specs = new RefSpec("refs/heads/master:refs/heads/master");

		{ // Push into remote from local one.

			StoredConfig config = db.getConfig();
			RemoteConfig remoteConfig = new RemoteConfig(config, remote);
			remoteConfig.addURI(new URIish(uri));
			remoteConfig.update(config);
			config.save();

			Git git = Git.open(dirOne);
			git.checkout().setName("master").call();
			git.push().setRemote(remote).setRefSpecs(specs).call();
			git.close();

			File fileStatic = new File(dirOne, nameStatic);
			assertTrue("Provided by setup", fileStatic.exists());

		}

		{ // Clone from remote into local two.

			File fileStatic = new File(dirTwo, nameStatic);
			assertFalse("Not Provided by setup", fileStatic.exists());

			Git git = Git.cloneRepository().setURI(uri).setDirectory(dirTwo)
					.call();
			git.close();

			assertTrue("Provided by clone", fileStatic.exists());
		}

		{ // Verify static file content.
			File fileOne = new File(dirOne, nameStatic);
			File fileTwo = new File(dirTwo, nameStatic);
			verifyFileContent(fileOne, fileTwo);
		}

		{ // Verify new file commit and push from local one.

			File fileDynamic = new File(dirOne, nameDynamic);
			assertFalse("Not Provided by setup", fileDynamic.exists());
			FileUtils.createNewFile(fileDynamic);
			textWrite(fileDynamic, nameDynamic);
			assertTrue("Provided by create", fileDynamic.exists());
			assertTrue("Need content to encrypt", fileDynamic.length() > 0);

			Git git = Git.open(dirOne);
			git.add().addFilepattern(nameDynamic).call();
			git.commit().setMessage(nameDynamic).call();
			git.push().setRemote(remote).setRefSpecs(specs).call();
			git.close();

		}

		{ // Verify new file pull from remote into local two.

			File fileDynamic = new File(dirTwo, nameDynamic);
			assertFalse("Not Provided by setup", fileDynamic.exists());

			Git git = Git.open(dirTwo);
			git.pull().call();
			git.close();

			assertTrue("Provided by pull", fileDynamic.exists());
		}

		{ // Verify dynamic file content.
			File fileOne = new File(dirOne, nameDynamic);
			File fileTwo = new File(dirTwo, nameDynamic);
			verifyFileContent(fileOne, fileTwo);
		}

	}

}
