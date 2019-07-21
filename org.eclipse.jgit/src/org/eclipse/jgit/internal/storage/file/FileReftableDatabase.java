package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.internal.storage.reftable.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.FS;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Map;

public class FileReftableDatabase extends RefDatabase {

    @Override
    public boolean performsAtomicTransactions() {
        return true;
    }

    @NonNull
    @Override
    public BatchRefUpdate newBatchUpdate() {
        return null;
    }

    @Override
    public RefUpdate newUpdate(String name, boolean detach) throws IOException {
        return null;
    }

    @Override
    public Ref exactRef(String name) throws IOException {
        return null;
    }

    @Override
    public List<Ref> getRefs() throws IOException {
        return super.getRefs();
    }

    @Override
    public Map<String, Ref> getRefs(String prefix) throws IOException {
        return null;
    }

    @Override
    public List<Ref> getAdditionalRefs() throws IOException {
        return null;
    }

    @Override
    public Ref peel(Ref ref) throws IOException {
        return null;
    }

    @Override
    public RefRename newRename(String fromName, String toName) throws IOException {
        return null;
    }

    @Override
    public boolean isNameConflicting(String name) throws IOException {
        return false;
    }

    @Override
    public void close() {

    }

    @Override
    public void create() throws IOException {

    }

}
