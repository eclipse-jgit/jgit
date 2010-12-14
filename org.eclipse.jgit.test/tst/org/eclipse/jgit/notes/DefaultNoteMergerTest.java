package org.eclipse.jgit.notes;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevBlob;

public class DefaultNoteMergerTest extends RepositoryTestCase {

	private TestRepository<Repository> tr;

	private ObjectReader reader;

	private ObjectInserter inserter;

	private DefaultNoteMerger merger;

	private Note baseNote;

	private RevBlob noteOn;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		tr = new TestRepository<Repository>(db);
		reader = db.newObjectReader();
		inserter = db.newObjectInserter();
		merger = new DefaultNoteMerger(reader, inserter);
		noteOn = tr.blob("a");
		baseNote = newNote("data");
	}

	@Override
	protected void tearDown() throws Exception {
		reader.release();
		inserter.release();
		super.tearDown();
	}

	public void testDeleteDelete() throws Exception {
		assertNull(merger.merge(baseNote, null, null));
	}

	public void testEditDelete() throws Exception {
		Note edit = newNote("edit");
		assertSame(merger.merge(baseNote, edit, null), edit);
		assertSame(merger.merge(baseNote, null, edit), edit);
	}

	public void testIdenticalEdit() throws Exception {
		Note edit = newNote("edit");
		assertSame(merger.merge(baseNote, edit, edit), edit);
	}

	public void testEditEdit() throws Exception {
		Note edit1 = newNote("edit1");
		Note edit2 = newNote("edit2");

		Note result = merger.merge(baseNote, edit1, edit2);
		assertEquals(result, noteOn); // same note
		assertEquals(result.getData(), tr.blob("edit1edit2"));

		result = merger.merge(baseNote, edit2, edit1);
		assertEquals(result, noteOn); // same note
		assertEquals(result.getData(), tr.blob("edit2edit1"));
	}

	public void testIdenticalAdd() throws Exception {
		Note add = newNote("add");
		assertSame(merger.merge(null, add, add), add);
	}

	public void testAddAdd() throws Exception {
		Note add1 = newNote("add1");
		Note add2 = newNote("add2");

		Note result = merger.merge(baseNote, add1, add2);
		assertEquals(result, noteOn); // same note
		assertEquals(result.getData(), tr.blob("add1add2"));

		result = merger.merge(baseNote, add2, add1);
		assertEquals(result, noteOn); // same note
		assertEquals(result.getData(), tr.blob("add2add1"));
	}

	private Note newNote(String data) throws Exception {
		return new Note(noteOn, tr.blob(data));
	}
}
