package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.internal.storage.reftable.*;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.FileUtils;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class FileReftableStack implements AutoCloseable, ReftableStack {

    public class FileReftableBatchRefUpdate extends ReftableBatchRefUpdate {
        ReftableConfig config;
        public FileReftableBatchRefUpdate(FileReftableDatabase db, Repository repository) {
            super(db, lock, repository, ()->FileReftableStack.this);

            // XXX get this from the repo config.
            config = new ReftableConfig();
        }

        @Override
        protected void applyUpdates(List<Ref> newRefs, List<ReceiveCommand> pending) throws IOException {
            LockFile lock = new LockFile(stackPath);
            try {
                lock.lockForAppend();
                if (!uptodate()) {
                    throw new RetryException();
                }

                // It would be nice if the reftable format put all metadata at
                // the end, so we could just take a reftable writer as input to this method.

                // XXX .log extension?
                String fn = filename(nextUpdateIndex());

                File tmpTable = File.createTempFile(fn + "_", ".ref",
                        stackPath.getParentFile());
                File dest = new File(reftableDir(), fn);

                try (FileOutputStream fos = new FileOutputStream(tmpTable)) {
                    write(fos, config, newRefs, pending);
                }

                FileInputStream readerStream = new FileInputStream(tmpTable);

                FileUtils.rename(tmpTable, dest,
                        StandardCopyOption.ATOMIC_MOVE);
                lock.write((fn + "\n").getBytes());
                if (!lock.commit()) {
                    FileUtils.delete(dest);
                    throw new RetryException();
                }

                StackEntry se = new StackEntry();
                se.name = fn;
                se.reftableReader = new ReftableReader(BlockSource.from(readerStream));
                stack.add(se);
            } finally {
                lock.unlock();
            }
        }
    }


    public static FileReftableStack open(File path) throws  IOException {
        while (true) {
            try {
                FileReftableStack s = new FileReftableStack(path);
                return s;
            } catch (RetryException e) {

            }
        }
    }

    public boolean uptodate() throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(stackPath))) {
            for (StackEntry e : stack) {
                if (!e.name.equals(br.readLine())) {
                    return false;
                }
            }
            if (br.readLine() != null) {
                return false;
            }
        }
        return true;
    }

    private String filename(long index) {
        // 64 bit should use %020 ?
        return String.format("%06d", index);
    }


    private static class RetryException extends IOException {

    }

    private static class StackEntry {
        String name;
        ReftableReader reftableReader;
    }

    ReentrantLock lock; // Why is this necessary?
    File stackPath;
    private List<StackEntry> stack;
    private MergedReftable merged;

    public MergedReftable getReftable() { return merged; }

    private static void closeStack(List<StackEntry> stack) {
        for (StackEntry entry : stack) {
            try {
                entry.reftableReader.close();
            } catch (Exception e){
                // we are reading. We don't care about exceptions.
            }
        }
    }

    @Override
    public void close() {
        closeStack(stack);
    }

    private File reftableDir() {
        return new File(stackPath.getParentFile(), Constants.REFTABLE);
    }

    private FileReftableStack(File path) throws IOException, RetryException {
        stackPath = path;

        List<StackEntry> stack = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(stackPath))) {
            String line;

            try {
                while ((line = br.readLine()) != null) {
                    File subtable = new File(reftableDir(), line);
                    FileInputStream is;
                    try {
                        is = new FileInputStream(subtable);
                    } catch (FileNotFoundException e) {
                        throw new RetryException();
                    }

                    StackEntry e = new StackEntry();
                    e.reftableReader = new ReftableReader(BlockSource.from(is));
                    e.name = line;
                    stack.add(e);
                }
            } catch (Exception e) {
                closeStack(stack);
                throw e;
            }
        }
        this.stack = stack;
        this.merged = new MergedReftable(stack.stream().map(x -> x.reftableReader).collect(Collectors.toList()));
    }


    @Override
    public long nextUpdateIndex() throws IOException {
        long updateIndex = 0;
        for (StackEntry e : stack) {
                updateIndex = Math.max(updateIndex, e.reftableReader.maxUpdateIndex());

        }
        return updateIndex + 1;
    }

}
