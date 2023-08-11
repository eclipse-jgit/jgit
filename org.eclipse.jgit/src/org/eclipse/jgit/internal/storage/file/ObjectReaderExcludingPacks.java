/*
 * Copyright (C) 2023, GerritForge Ltd.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.internal.storage.pack.CachedPack;
import org.eclipse.jgit.internal.storage.pack.ObjectReuseAsIs;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class ObjectReaderExcludingPacks extends ObjectReader.Filter implements ObjectReuseAsIs {
    private final WindowCursor windowCursor;

    static ObjectReader wrap(ObjectReader reader, Set<Pack> excludedPacks) {
        if(reader instanceof WindowCursor &&
                excludedPacks != null && excludedPacks.size() > 0) {
            return new ObjectReaderExcludingPacks((WindowCursor) reader, excludedPacks);
        }

        return reader;
    }

    ObjectReaderExcludingPacks(WindowCursor windowCursor, Set<Pack> excludedPacks) {
        this.windowCursor = new WindowCursor(new FileObjectDatabaseExcludingPacks(windowCursor.db, excludedPacks));
    }

    /**
     * @return delegate ObjectReader to handle all processing.
     * @since 4.4
     */
    @Override
    protected ObjectReader delegate() {
        return windowCursor;
    }

    @Override
    public ObjectToPack newObjectToPack(AnyObjectId objectId, int type) {
        return windowCursor.newObjectToPack(objectId, type);
    }

    @Override
    public void selectObjectRepresentation(PackWriter packer, ProgressMonitor monitor, Iterable<ObjectToPack> objects) throws IOException, MissingObjectException {
        windowCursor.selectObjectRepresentation(packer, monitor, objects);
    }

    @Override
    public void writeObjects(PackOutputStream out, List<ObjectToPack> list) throws IOException {
        windowCursor.writeObjects(out, list);
    }

    @Override
    public void copyObjectAsIs(PackOutputStream out, ObjectToPack otp, boolean validate) throws IOException, StoredObjectRepresentationNotAvailableException {
        windowCursor.copyObjectAsIs(out, otp, validate);
    }

    @Override
    public void copyPackAsIs(PackOutputStream out, CachedPack pack) throws IOException {
        windowCursor.copyPackAsIs(out, pack);
    }

    @Override
    public Collection<CachedPack> getCachedPacksAndUpdate(BitmapIndex.BitmapBuilder needBitmap) throws IOException {
        return windowCursor.getCachedPacksAndUpdate(needBitmap);
    }

    private static class FileObjectDatabaseExcludingPacks extends FileObjectDatabase {
        private final FileObjectDatabase fileDb;
        private final Set<Pack> excludedPacks;

        FileObjectDatabaseExcludingPacks(FileObjectDatabase fileDb, Set<Pack> excludedPacks) {
            this.fileDb = fileDb;
            this.excludedPacks = excludedPacks;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ObjectReader newReader() {
            return fileDb.newReader();
        }

        /**
         * {@inheritDoc}
         */
        public ObjectDirectoryInserter newInserter() {
            return fileDb.newInserter();
        }

        public void resolve(Set<ObjectId> matches, AbbreviatedObjectId id)throws IOException {
            fileDb.resolve(matches, id);
        }

        public Config getConfig() {
            return fileDb.getConfig();
        }

        public FS getFS() {
            return fileDb.getFS();
        }

        @Override
        public Set<ObjectId> getShallowCommits()throws IOException {
            return fileDb.getShallowCommits();
        }

        public void selectObjectRepresentation(PackWriter packer, ObjectToPack otp, WindowCursor curs)throws IOException {
            fileDb.selectObjectRepresentation(packer, otp, curs);
        }

        public File getDirectory() {
            return fileDb.getDirectory();
        }

        public File fileFor(AnyObjectId id) {
            return fileDb.fileFor(id);
        }

        public ObjectLoader openObject(WindowCursor curs, AnyObjectId objectId)throws IOException {
            return fileDb.openObject(curs, objectId);
        }

        public long getObjectSize(WindowCursor curs, AnyObjectId objectId)throws IOException {
            return fileDb.getObjectSize(curs, objectId);
        }

        public ObjectLoader openLooseObject(WindowCursor curs, AnyObjectId id)throws IOException {
            return fileDb.openLooseObject(curs, id);
        }

        public FileObjectDatabase.InsertLooseObjectResult insertUnpackedObject(File tmp, ObjectId id, boolean createDuplicate)throws IOException {
            return fileDb.insertUnpackedObject(tmp, id, createDuplicate);
        }

        public Pack openPack(File pack)throws IOException {
            return fileDb.openPack(pack);
        }

        public Collection<Pack> getPacks() {
            return fileDb.getPacks().stream().filter(p -> !excludedPacks.contains(p)).collect(Collectors.toList());
        }

        /**
         * Does this database exist yet?
         *
         * @return true if this database is already created; false if the caller
         * should invoke {@link #create()} to create this database location.
         */
        public boolean exists() {
            return fileDb.exists();
        }

        /**
         * Initialize a new object database at this location.
         *
         * @throws IOException the database could not be created.
         */
        public void create() throws IOException {
            fileDb.create();
        }

        /**
         * Close any resources held by this database.
         */
        @Override
        public void close() {
            fileDb.close();
        }

        /**
         * Does the requested object exist in this database?
         * <p>
         * This is a one-shot call interface which may be faster than allocating a
         * {@link #newReader()} to perform the lookup.
         *
         * @param objectId identity of the object to test for existence of.
         * @return true if the specified object is stored in this database.
         * @throws IOException the object store cannot be accessed.
         */
        @Override
        public boolean has(AnyObjectId objectId) throws IOException {
            return fileDb.has(objectId);
        }

        /**
         * Open an object from this database.
         * <p>
         * This is a one-shot call interface which may be faster than allocating a
         * {@link #newReader()} to perform the lookup.
         *
         * @param objectId identity of the object to open.
         * @return a {@link ObjectLoader} for accessing the object.
         * @throws MissingObjectException the object does not exist.
         * @throws IOException            the object store cannot be accessed.
         */
        @Override
        public ObjectLoader open(AnyObjectId objectId) throws IOException {
            return fileDb.open(objectId);
        }

        /**
         * Open an object from this database.
         * <p>
         * This is a one-shot call interface which may be faster than allocating a
         * {@link #newReader()} to perform the lookup.
         *
         * @param objectId identity of the object to open.
         * @param typeHint hint about the type of object being requested, e.g.
         *                 {@link Constants#OBJ_BLOB};
         *                 {@link ObjectReader#OBJ_ANY} if the
         *                 object type is not known, or does not matter to the caller.
         * @return a {@link ObjectLoader} for accessing the
         * object.
         * @throws MissingObjectException       the object does not exist.
         * @throws IncorrectObjectTypeException typeHint was not OBJ_ANY, and the object's actual type does
         *                                      not match typeHint.
         * @throws IOException                  the object store cannot be accessed.
         */  @Override public ObjectLoader open(AnyObjectId objectId, int typeHint) throws MissingObjectException, IncorrectObjectTypeException, IOException {
            return fileDb.open(objectId, typeHint);
        }

        /**
         * Create a new cached database instance over this database. This instance might
         * optimize queries by caching some information about database. So some modifications
         * done after instance creation might fail to be noticed.
         *
         * @return new cached database instance
         */  public ObjectDatabase newCachedDatabase() {
            return fileDb.newCachedDatabase();}
    }
}
