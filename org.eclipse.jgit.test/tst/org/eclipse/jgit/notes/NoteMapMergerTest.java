package org.eclipse.jgit.notes;

import java.io.IOException;
import java.util.Iterator;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;

public class NoteMapMergerTest extends RepositoryTestCase {
	private TestRepository<Repository> tr;

	private ObjectReader reader;

	private ObjectInserter inserter;

	private NoteMap noRoot;

	private NoteMap empty;

	private NoteMap map1;

	private NoteMap map2;

	private RevBlob noteAId;

	private RevBlob noteAContent;

	private RevBlob noteBId;

	private RevBlob noteBContent;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		tr = new TestRepository<Repository>(db);
		reader = db.newObjectReader();
		inserter = db.newObjectInserter();

		noRoot = NoteMap.newMap(null, reader);
		empty = NoteMap.newEmptyMap();

		noteAId = tr.blob("a");
		noteAContent = tr.blob("data01");

		RevCommit sampleTree = tr.commit()
			.add(noteAId.name(), noteAContent)
			.create();
		tr.parseBody(sampleTree);

		map1 = NoteMap.read(reader, sampleTree);

		map2 = NoteMap.read(reader, sampleTree);
		noteBId = tr.blob("b");
		noteBContent = tr.blob("data02");
		map2.set(noteBId, noteBContent);
	}

	@Override
	protected void tearDown() throws Exception {
		reader.release();
		inserter.release();
		super.tearDown();
	}

	public void testNoChange() throws IOException {
		NoteMapMerger merger = new NoteMapMerger(db, null);
		NoteMap result;

		assertEquals(0, countNotes(merger.merge(noRoot, noRoot, noRoot)));
		assertEquals(0, countNotes(merger.merge(empty, empty, empty)));

		result = merger.merge(map1, map1, map1);
		assertEquals(1, countNotes(result));
		assertEquals(result.get(noteAId), noteAContent);
	}

	public void testOursEqualsTheirs() throws Exception {
		NoteMapMerger merger = new NoteMapMerger(db, null);
		NoteMap result;

		assertEquals(0, countNotes(merger.merge(empty, noRoot, noRoot)));
		assertEquals(0, countNotes(merger.merge(map1, noRoot, noRoot)));

		assertEquals(0, countNotes(merger.merge(noRoot, empty, empty)));
		assertEquals(0, countNotes(merger.merge(map1, empty, empty)));

		result = merger.merge(noRoot, map1, map1);
		assertEquals(1, countNotes(result));
		assertEquals(result.get(noteAId), noteAContent);

		result = merger.merge(empty, map1, map1);
		assertEquals(1, countNotes(result));
		assertEquals(result.get(noteAId), noteAContent);

		result = merger.merge(map2, map1, map1);
		assertEquals(1, countNotes(result));
		assertEquals(result.get(noteAId), noteAContent);
	}

	public void testBaseEqualsOurs() throws Exception {
		NoteMapMerger merger = new NoteMapMerger(db, null);
		NoteMap result;

		assertEquals(0, countNotes(merger.merge(noRoot, noRoot, empty)));
		result = merger.merge(noRoot, noRoot, map1);
		assertEquals(1, countNotes(result));
		assertEquals(result.get(noteAId), noteAContent);

		assertEquals(0, countNotes(merger.merge(empty, empty, noRoot)));
		result = merger.merge(empty, empty, map1);
		assertEquals(1, countNotes(result));
		assertEquals(result.get(noteAId), noteAContent);

		assertEquals(0, countNotes(merger.merge(map1, map1, noRoot)));
		assertEquals(0, countNotes(merger.merge(map1, map1, empty)));
		result = merger.merge(map1, map1, map2);
		assertEquals(2, countNotes(result));
		assertEquals(result.get(noteAId), noteAContent);
		assertEquals(result.get(noteBId), noteBContent);
	}

	public void testBaseEqualsTheirs() throws Exception {
		NoteMapMerger merger = new NoteMapMerger(db, null);
		NoteMap result;

		assertEquals(0, countNotes(merger.merge(noRoot, empty, noRoot)));
		result = merger.merge(noRoot, map1, noRoot);
		assertEquals(1, countNotes(result));
		assertEquals(result.get(noteAId), noteAContent);

		assertEquals(0, countNotes(merger.merge(empty, noRoot, empty)));
		result = merger.merge(empty, map1, empty);
		assertEquals(1, countNotes(result));
		assertEquals(result.get(noteAId), noteAContent);

		assertEquals(0, countNotes(merger.merge(map1, noRoot, map1)));
		assertEquals(0, countNotes(merger.merge(map1, empty, map1)));
		result = merger.merge(map1, map2, map1);
		assertEquals(2, countNotes(result));
		assertEquals(result.get(noteAId), noteAContent);
		assertEquals(result.get(noteBId), noteBContent);
	}

	private static int countNotes(NoteMap result) {
		int c = 0;
		Iterator<Note> it = result.iterator();
		while (it.hasNext()) {
			it.next();
			c++;
		}
		return c;
	}

}
