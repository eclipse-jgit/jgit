package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.GitDateFormatter.Format;
import org.junit.Before;
import org.junit.Test;

public class GitDateFormatterTest {

	private MockSystemReader mockSystemReader;

	private PersonIdent ident;

	@Before
	public void setUp() {
		mockSystemReader = new MockSystemReader() {
			@Override
			public long getCurrentTime() {
				return 1318125997291L;
			}
		};
		SystemReader.setInstance(mockSystemReader);
		ident = RawParseUtils
				.parsePersonIdent("A U Thor <author@example.com> 1316560165 -0400");
	}

	@Test
	public void DEFAULT() {
		assertEquals("Tue Sep 20 19:09:25 2011 -0400", new GitDateFormatter(
				Format.DEFAULT).formatDate(ident));
	}

	@Test
	public void RELATIVE() {
		assertEquals("3 weeks ago",
				new GitDateFormatter(Format.RELATIVE).formatDate(ident));
	}

	@Test
	public void LOCAL() {
		assertEquals("Tue Sep 20 19:39:25 2011", new GitDateFormatter(
				Format.LOCAL).formatDate(ident));
	}

	@Test
	public void ISO() {
		assertEquals("2011-09-20 19:09:25 -0400", new GitDateFormatter(
				Format.ISO).formatDate(ident));
	}

	@Test
	public void RFC() {
		assertEquals("Tue, 20 Sep 2011 19:09:25 -0400", new GitDateFormatter(
				Format.RFC).formatDate(ident));
	}

	@Test
	public void SHORT() {
		assertEquals("2011-09-20",
				new GitDateFormatter(Format.SHORT).formatDate(ident));
	}

	@Test
	public void RAW() {
		assertEquals("1316560165 -0400",
				new GitDateFormatter(Format.RAW).formatDate(ident));
	}

	@Test
	public void LOCALE() {
		assertEquals("2011-09-20 19:09:25 -0400", new GitDateFormatter(
				Format.LOCALE).formatDate(ident));
	}

	@Test
	public void LOCALELOCAL() {
		assertEquals("2011-09-20 19:39:25", new GitDateFormatter(
				Format.LOCALELOCAL).formatDate(ident));
	}
}
