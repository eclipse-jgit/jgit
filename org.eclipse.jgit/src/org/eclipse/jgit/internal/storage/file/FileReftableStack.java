package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.internal.storage.reftable.Reftable;
import org.eclipse.jgit.internal.storage.reftable.ReftableReader;
import org.eclipse.jgit.internal.storage.reftable.ReftableStack;
import org.eclipse.jgit.lib.Constants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public class FileReftableStack implements ReftableStack {
    private FileReftableStack(File path) throws IOException {
        stackPath = path;

        List<StackEntry> stack = new ArrayList<>();
        ReftableReader last = null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                        new FileInputStream(stackPath), UTF_8))) {
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
                    if (last != null) {
                        // XXX this check should be in MergedReftable
                        if (last.maxUpdateIndex() >= e.reftableReader.minUpdateIndex()) {
                            throw new IllegalStateException(
                                    String.format("index numbers not increasing %s: min %d, last max %d", line,
                                            e.reftableReader.minUpdateIndex(),
                                            last.maxUpdateIndex()));
                        }
                    }
                    last = e.reftableReader;

                    e.name = line;
                    stack.add(e);
                }
            } catch (Exception e) {
                closeStack(stack);
                throw e;
            }
        } catch (FileNotFoundException e) {
            // stack is not there yet: empty repo.
        }
        this.stack = stack;

    }

    @Override
    public List<Reftable> readers() {
        return stack.stream().map(x -> x.reftableReader).collect(Collectors.toList());
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
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(stackPath), UTF_8))) {
            for (StackEntry e : stack) {
                if (!e.name.equals(br.readLine())) {
                    return false;
                }
            }
            if (br.readLine() != null) {
                return false;
            }
        } catch (FileNotFoundException e) {
            return stack.isEmpty();
        }
        return true;
    }

    private static class RetryException extends IOException {

    }

    private static class StackEntry {
        String name;
        ReftableReader reftableReader;
    }

    File stackPath;
    private List<StackEntry> stack;

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

    @Override
    public long nextUpdateIndex() throws IOException {
        long updateIndex = 0;
        for (StackEntry e : stack) {
                updateIndex = Math.max(updateIndex, e.reftableReader.maxUpdateIndex());

        }
        return updateIndex + 1;
    }

}
