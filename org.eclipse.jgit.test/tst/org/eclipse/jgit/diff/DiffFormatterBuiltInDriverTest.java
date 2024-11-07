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
		String body = "#include <stdio.h>\n" + "#include <string.h>\n"
				+ "#include <ctype.h> \n" + "\n"
				+ "void getGreeting(char *result, const char *name) {\n"
				+ "    sprintf(result, \"Hello, %s!\", name);\n" + "}\n" + "\n"
				+ "void getFarewell(char *result, const char *name) {\n"
				+ "    sprintf(result, \"Goodbye, %s. Have a great day!\", name);\n"
				+ "}\n" + "\n" + "void toLower(char *str) {\n"
				+ "    for (int i = 0; str[i]; i++) {\n"
				+ "        str[i] = tolower(str[i]);\n" + "    }\n" + "}\n"
				+ "\n"
				+ "void getPersonalizedGreeting(char *result, const char *name, const char *timeOfDay) {\n"
				+ "    char timeOfDayLower[50];\n"
				+ "    strcpy(timeOfDayLower, timeOfDay);\n"
				+ "    toLower(timeOfDayLower);\n"
				+ "    if (strcmp(timeOfDayLower, \"morning\") == 0) {\n"
				+ "        sprintf(result, \"Good morning, %s\", name);\n"
				+ "    } else if (strcmp(timeOfDayLower, \"afternoon\") == 0) {\n"
				+ "        sprintf(result, \"Good afternoon, %s\", name);\n"
				+ "    } else if (strcmp(timeOfDayLower, \"evening\") == 0) {\n"
				+ "        sprintf(result, \"Good evening, %s\", name);\n"
				+ "    } else {\n"
				+ "        sprintf(result, \"Good day, %s\", name);\n"
				+ "    }\n" + "}\n" + "\n" + "int main() {\n"
				+ "    char result[100];\n"
				+ "    getGreeting(result, \"foo\");\n"
				+ "    printf(\"%s\\n\", result);\n"
				+ "    getFarewell(result, \"bar\");\n"
				+ "    printf(\"%s\\n\", result);\n"
				+ "    getPersonalizedGreeting(result, \"baz\", \"morning\");\n"
				+ "    printf(\"%s\\n\", result);\n" + "    return 0;\n"
				+ "}\n";
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
		String body =
				"/dts-v1/;\n" + "\n" + "/ {\n" + "	model = \"Example Board\";\n"
						+ "	compatible = \"example,board\";\n" + "	cpus {\n"
						+ "		cpu@0 {\n" + "			device_type = \"cpu\";\n"
						+ "			compatible = \"arm,cortex-a9\";\n"
						+ "			reg = <0>;\n" + "		};\n" + "	};\n"
						+ "\n" + "	memory {\n"
						+ "		device_type = \"memory\";\n"
						+ "		reg = <0x80000000 0x20000000>;\n" + "	};\n"
						+ "\n" + "	uart0: uart@101f1000 {\n"
						+ "		compatible = \"ns16550a\";\n"
						+ "		reg = <0x101f1000 0x1000>;\n"
						+ "		interrupts = <5>;\n"
						+ "		clock-frequency = <24000000>;\n" + "	};\n"
						+ "};\n";

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
		String body = "public class Greeting {\n"
				+ "    public String getGreeting(String name) {\n"
				+ "        String msg = \"Hello, \" + name + \"!\";\n"
				+ "        return msg;\n" + "    }\n" + "\n"
				+ "    public String getFarewell(String name) {\n"
				+ "        String msg = \"Goodbye, \" + name + \". Have a great day!\";\n"
				+ "        return msg;\n" + "    }\n" + "\n"
				+ "    public String getPersonalizedGreeting(String name, String timeOfDay) {\n"
				+ "        String personalizedMessage;\n"
				+ "        switch (timeOfDay.toLowerCase()) {\n"
				+ "            case \"morning\":\n"
				+ "                msg = \"Good morning, \" + name;\n"
				+ "                break;\n"
				+ "            case \"afternoon\":\n"
				+ "                msg = \"Good afternoon, \" + name;\n"
				+ "                break;\n" + "            case \"evening\":\n"
				+ "                msg = \"Good evening, \" + name;\n"
				+ "                break;\n" + "            default:\n"
				+ "                msg = \"Good day, \" + name;\n"
				+ "                break;\n" + "        }\n"
				+ "        return msg;\n" + "    }\n" + "\n"
				+ "    public static void main(String[] args) {\n"
				+ "        Greeting greeting = new Greeting();\n"
				+ "        System.out.println(greeting.getGreeting(\"foo\"));\n"
				+ "        System.out.println(greeting.getFarewell(\"bar\"));\n"
				+ "        System.out.println(greeting.getPersonalizedGreeting(\"baz\", \"morning\"));\n"
				+ "    }\n" + "}\n";
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
		String body =
				"class Greeting:\n" + "    def get_greeting(self, name):\n"
						+ "        greeting_message = f'Hello, {name}!'\n"
						+ "        return greeting_message\n" + "\n"
						+ "    def get_farewell(self, name):\n"
						+ "        farewell_message = f'Goodbye, {name}. Have a great day!'\n"
						+ "        return farewell_message\n" + "\n"
						+ "    def get_personalized_greeting(self, name, time_of_day):\n"
						+ "        time_of_day = time_of_day.lower()\n"
						+ "        if time_of_day == 'morning':\n"
						+ "            personalized_message = f'Good morning, {name}'\n"
						+ "        elif time_of_day == 'afternoon':\n"
						+ "            personalized_message = f'Good afternoon, {name}'\n"
						+ "        elif time_of_day == 'evening':\n"
						+ "            personalized_message = f'Good evening, {name}'\n"
						+ "        else:\n"
						+ "            personalized_message = f'Good day, {name}'\n"
						+ "        return personalized_message\n" + "\n"
						+ "if __name__ == '__main__':\n"
						+ "    greeting = Greeting()\n"
						+ "    print(greeting.get_greeting('foo'))\n"
						+ "    print(greeting.get_farewell('bar'))\n"
						+ "    print(greeting.get_personalized_greeting('baz', 'morning'))\n";
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
		String body = "struct Greeting;\n" + "\n" + "impl Greeting {\n"
				+ "    fn get_greeting(&self, name: &str) -> String {\n"
				+ "        format!(\"Hello, {}!\", name)\n" + "    }\n" + "\n"
				+ "    fn get_farewell(&self, name: &str) -> String {\n"
				+ "        format!(\"Goodbye, {}. Have a great day!\", name)\n"
				+ "    }\n" + "\n"
				+ "    fn get_personalized_greeting(&self, name: &str, time_of_day: &str) -> String {\n"
				+ "        match time_of_day.to_lowercase().as_str() {\n"
				+ "            \"morning\" => format!(\"Good morning, {}\", name),\n"
				+ "            \"afternoon\" => format!(\"Good afternoon, {}\", name),\n"
				+ "            \"evening\" => format!(\"Good evening, {}\", name),\n"
				+ "            _ => format!(\"Good day, {}\", name),\n"
				+ "        }\n" + "    }\n" + "}\n" + "\n" + "fn main() {\n"
				+ "    let greeting = Greeting;\n"
				+ "    println!(\"{}\", greeting.get_greeting(\"foo\"));\n"
				+ "    println!(\"{}\", greeting.get_farewell(\"bar\"));\n"
				+ "    println!(\"{}\", greeting.get_personalized_greeting(\"baz\", \"morning\"));\n"
				+ "}\n";
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
