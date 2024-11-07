/*
 * Copyright (c) 2024 Qualcomm Innovation Center, Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diff;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.junit.Test;

public class DiffFormatterBuiltInDriverTest extends RepositoryTestCase {
	@Test
	public void testCppDriver() throws Exception {
		String fileName = "greeting.c";
		String body = """
				#include <stdio.h>
				#include <string.h>
				#include <ctype.h>

				void getGreeting(char *result, const char *name) {
				    sprintf(result, "Hello, %s!", name);
				}

				void getFarewell(char *result, const char *name) {
				    sprintf(result, "Goodbye, %s. Have a great day!", name);
				}

				void toLower(char *str) {
				    for (int i = 0; str[i]; i++) {
				        str[i] = tolower(str[i]);
				    }
				}

				void getPersonalizedGreeting(char *result, const char *name, const char *timeOfDay) {
				    char timeOfDayLower[50];
				    strcpy(timeOfDayLower, timeOfDay);
				    toLower(timeOfDayLower);
				    if (strcmp(timeOfDayLower, "morning") == 0) {
				        sprintf(result, "Good morning, %s", name);
				    } else if (strcmp(timeOfDayLower, "afternoon") == 0) {
				        sprintf(result, "Good afternoon, %s", name);
				    } else if (strcmp(timeOfDayLower, "evening") == 0) {
				        sprintf(result, "Good evening, %s", name);
				    } else {
				        sprintf(result, "Good day, %s", name);
				    }
				}

				int main() {
				    char result[100];
				    getGreeting(result, "foo");
				    printf("%s\\n", result);
				    getFarewell(result, "bar");
				    printf("%s\\n", result);
				    getPersonalizedGreeting(result, "baz", "morning");
				    printf("%s\\n", result);
				    return 0;
				}
				""";
		RevCommit c1;
		RevCommit c2;
		try (Git git = new Git(db)) {
			createCommit(git, ".gitattributes", "*.c   diff=cpp");
			c1 = createCommit(git, fileName, body);
			c2 = createCommit(git, fileName,
					body.replace("Good day", "Greetings")
							.replace("baz", "qux"));
		}
		try (ByteArrayOutputStream os = new ByteArrayOutputStream();
				DiffFormatter diffFormatter = new DiffFormatter(os)) {
			String actual = getHunkHeaders(c1, c2, os, diffFormatter);
			String expected =
					"@@ -27,7 +27,7 @@ void getPersonalizedGreeting(char *result, const char *name, const char *timeOfD\n"
							+ "@@ -37,7 +37,7 @@ int main() {";
			assertEquals(expected, actual);
		}
	}

	@Test
	public void testDtsDriver() throws Exception {
		String fileName = "sample.dtsi";
		String body = """
				/dts-v1/;

				/ {
					model = "Example Board";
					compatible = "example,board";
					cpus {
						cpu@0 {
							device_type = "cpu";
							compatible = "arm,cortex-a9";
							reg = <0>;
						};
					};

					memory {
						device_type = "memory";
						reg = <0x80000000 0x20000000>;
					};

					uart0: uart@101f1000 {
						compatible = "ns16550a";
						reg = <0x101f1000 0x1000>;
						interrupts = <5>;
						clock-frequency = <24000000>;
					};
				};
				""";
		RevCommit c1;
		RevCommit c2;
		try (Git git = new Git(db)) {
			createCommit(git, ".gitattributes", "*.dtsi   diff=dts");
			c1 = createCommit(git, fileName, body);
			c2 = createCommit(git, fileName,
					body.replace("clock-frequency = <24000000>",
							"clock-frequency = <48000000>"));
		}
		try (ByteArrayOutputStream os = new ByteArrayOutputStream();
				DiffFormatter diffFormatter = new DiffFormatter(os)) {
			String actual = getHunkHeaders(c1, c2, os, diffFormatter);
			String expected = "@@ -20,6 +20,6 @@ uart0: uart@101f1000 {";
			assertEquals(expected, actual);
		}
	}

	@Test
	public void testJavaDriver() throws Exception {
		String fileName = "Greeting.java";
		String body = """
				public class Greeting {
				    public String getGreeting(String name) {
				        String msg = "Hello, " + name + "!";
				        return msg;
				    }

				    public String getFarewell(String name) {
				        String msg = "Goodbye, " + name + ". Have a great day!";
				        return msg;
				    }

				    public String getPersonalizedGreeting(String name, String timeOfDay) {
				        String personalizedMessage;
				        switch (timeOfDay.toLowerCase()) {
				            case "morning":
				                msg = "Good morning, " + name;
				                break;
				            case "afternoon":
				                msg = "Good afternoon, " + name;
				                break;
				            case "evening":
				                msg = "Good evening, " + name;
				                break;
				            default:
				                msg = "Good day, " + name;
				                break;
				        }
				        return msg;
				    }

				    public static void main(String[] args) {
				        Greeting greeting = new Greeting();
				        System.out.println(greeting.getGreeting("foo"));
				        System.out.println(greeting.getFarewell("bar"));
				        System.out.println(greeting.getPersonalizedGreeting("baz", "morning"));
				    }
				}
				""";
		RevCommit c1;
		RevCommit c2;
		try (Git git = new Git(db)) {
			createCommit(git, ".gitattributes", "*.java   diff=java");
			c1 = createCommit(git, fileName, body);
			c2 = createCommit(git, fileName,
					body.replace("Good day", "Greetings")
							.replace("baz", "qux"));
		}
		try (ByteArrayOutputStream os = new ByteArrayOutputStream();
				DiffFormatter diffFormatter = new DiffFormatter(os)) {
			String actual = getHunkHeaders(c1, c2, os, diffFormatter);
			String expected =
					"@@ -22,7 +22,7 @@ public String getPersonalizedGreeting(String name, String timeOfDay) {\n"
							+ "@@ -32,6 +32,6 @@ public static void main(String[] args) {";
			assertEquals(expected, actual);
		}
	}

	@Test
	public void testPythonDriver() throws Exception {
		String fileName = "greeting.py";
		String body = """
				class Greeting:
				    def get_greeting(self, name):
				        greeting_message = f"Hello, {name}!"
				        return greeting_message

				    def get_farewell(self, name):
				        farewell_message = f"Goodbye, {name}. Have a great day!"
				        return farewell_message

				    def get_personalized_greeting(self, name, time_of_day):
				        time_of_day = time_of_day.lower()
				        if time_of_day == "morning":
				            personalized_message = f"Good morning, {name}"
				        elif time_of_day == "afternoon":
				            personalized_message = f"Good afternoon, {name}"
				        elif time_of_day == "evening":
				            personalized_message = f"Good evening, {name}"
				        else:
				            personalized_message = f"Good day, {name}"
				        return personalized_message

				if __name__ == "__main__":
				    greeting = Greeting()
				    print(greeting.get_greeting("foo"))
				    print(greeting.get_farewell("bar"))
				    print(greeting.get_personalized_greeting("baz", "morning"))
				""";
		RevCommit c1;
		RevCommit c2;
		try (Git git = new Git(db)) {
			createCommit(git, ".gitattributes", "*.py   diff=python");
			c1 = createCommit(git, fileName, body);
			c2 = createCommit(git, fileName,
					body.replace("Good day", "Greetings"));
		}
		try (ByteArrayOutputStream os = new ByteArrayOutputStream();
				DiffFormatter diffFormatter = new DiffFormatter(os)) {
			String actual = getHunkHeaders(c1, c2, os, diffFormatter);
			String expected = "@@ -16,7 +16,7 @@ def get_personalized_greeting(self, name, time_of_day):";
			assertEquals(expected, actual);
		}
	}

	@Test
	public void testRustDriver() throws Exception {
		String fileName = "greeting.rs";
		String body = """
				struct Greeting;

				impl Greeting {
				    fn get_greeting(&self, name: &str) -> String {
				        format!("Hello, {}!", name)
				    }

				    fn get_farewell(&self, name: &str) -> String {
				        format!("Goodbye, {}. Have a great day!", name)
				    }

				    fn get_personalized_greeting(&self, name: &str, time_of_day: &str) -> String {
				        match time_of_day.to_lowercase().as_str() {
				            "morning" => format!("Good morning, {}", name),
				            "afternoon" => format!("Good afternoon, {}", name),
				            "evening" => format!("Good evening, {}", name),
				            _ => format!("Good day, {}", name),
				        }
				    }
				}

				fn main() {
				    let greeting = Greeting;
				    println!("{}", greeting.get_greeting("foo"));
				    println!("{}", greeting.get_farewell("bar"));
				    println!("{}", greeting.get_personalized_greeting("baz", "morning"));
				}
				""";
		RevCommit c1;
		RevCommit c2;
		try (Git git = new Git(db)) {
			createCommit(git, ".gitattributes", "*.rs   diff=rust");
			c1 = createCommit(git, fileName, body);
			c2 = createCommit(git, fileName,
					body.replace("Good day", "Greetings")
							.replace("baz", "qux"));
		}
		try (ByteArrayOutputStream os = new ByteArrayOutputStream();
				DiffFormatter diffFormatter = new DiffFormatter(os)) {
			String actual = getHunkHeaders(c1, c2, os, diffFormatter);
			String expected =
					"@@ -14,7 +14,7 @@ fn get_personalized_greeting(&self, name: &str, time_of_day: &str) -> String {\n"
							+ "@@ -23,5 +23,5 @@ fn main() {";
			assertEquals(expected, actual);
		}
	}

	private String getHunkHeaders(RevCommit c1, RevCommit c2,
			ByteArrayOutputStream os, DiffFormatter diffFormatter)
			throws IOException {
		diffFormatter.setRepository(db);
		diffFormatter.format(new CanonicalTreeParser(null, db.newObjectReader(),
						c1.getTree()),
				new CanonicalTreeParser(null, db.newObjectReader(),
						c2.getTree()));
		diffFormatter.flush();
		return Arrays.stream(os.toString(StandardCharsets.UTF_8).split("\n"))
				.filter(line -> line.startsWith("@@"))
				.collect(Collectors.joining("\n"));
	}

	private RevCommit createCommit(Git git, String fileName, String body)
			throws IOException, GitAPIException {
		writeTrashFile(fileName, body);
		git.add().addFilepattern(".").call();
		return git.commit().setMessage("message").call();
	}
}
