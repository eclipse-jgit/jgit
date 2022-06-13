/*
 * Copyright (C) 2023, GerritForge Ltd
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk;

import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;

import java.io.IOException;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.stream.Stream;

/**
 * A queue of commits sorted by commit time order using a Java PriorityQueue.
 * For the commits with the same commit time insertion order will be preserved.
 */
public class DateRevQueue extends AbstractRevQueue {
    private PriorityQueue<RevCommitEntry> priorityQueue;
    private static final AtomicInteger sequence = new AtomicInteger(1);

    /**
     * Create an empty date queue.
     */
    public DateRevQueue() {
        this(false);
    }

    DateRevQueue(boolean firstParent) {
        this(firstParent, 1024);
    }

    /**
     * Create an empty date queue.
     *
     * @param firstParent treat first element as a parent
     * @param initialCapacity initial size of the queue
     *
     * @since 6.6
     */
    public DateRevQueue(boolean firstParent, int initialCapacity) {
        super(firstParent);
        initPriorityQueue(initialCapacity);
    }

    private void initPriorityQueue(int initialCapacity) {
                sequence.set(1);
                priorityQueue = new PriorityQueue<>(initialCapacity,
                Comparator.comparingInt((RevCommitEntry ent) -> ent.getEntry().getCommitTime())
                    .reversed()
                    .thenComparingInt(RevCommitEntry::getInsertSequenceNumber));
    }

    DateRevQueue(Generator s) throws MissingObjectException,
            IncorrectObjectTypeException, IOException {
        this(s.firstParent);
        for (; ; ) {
            final RevCommit c = s.next();
            if (c == null) {
                break;
            }
            add(c);
        }
    }

    void ensureCapacity(int capacity) {
        if(priorityQueue.size() < capacity) {
            Stream<RevCommitEntry> currentElements = priorityQueue.stream();
            initPriorityQueue(capacity);
            currentElements.forEach((entry) -> add(entry.getEntry()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void add(RevCommit c) {
        //PriorityQueue does not accept null values. To keep the same behaviour
        // do the same check and throw the same exception before creating entry
        if (c == null) {
            throw new NullPointerException();
        }
        priorityQueue.add(new RevCommitEntry(sequence.getAndIncrement(), c));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RevCommit next() {
        RevCommitEntry entry = priorityQueue.poll();
        return entry == null ? null : entry.getEntry();
    }

    /**
     * Peek at the next commit, without removing it.
     *
     * @return the next available commit; null if there are no commits left.
     */
    public RevCommit peek() {
        RevCommitEntry entry = priorityQueue.peek();
        return entry == null ? null : entry.getEntry();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        sequence.set(1);
        priorityQueue.clear();
    }

    @Override
    boolean everbodyHasFlag(int f) {
        return priorityQueue.stream().map(RevCommitEntry::getEntry).noneMatch(c -> (c.flags & f) == 0);
    }

    @Override
    boolean anybodyHasFlag(int f) {
        return priorityQueue.stream().map(RevCommitEntry::getEntry).anyMatch(c -> (c.flags & f) != 0);
    }

    @Override
    int outputType() {
        return outputType | SORT_COMMIT_TIME_DESC;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder s = new StringBuilder();
        for (RevCommitEntry e: priorityQueue) {
            describe(s, e.getEntry());
        }
        return s.toString();
    }

    private static class RevCommitEntry {
        private final int insertSequenceNumber;
        private final RevCommit entry;

        public RevCommitEntry(int insertSequenceNumber, RevCommit entry) {
            this.insertSequenceNumber = insertSequenceNumber;
            this.entry = entry;
        }

        public int getInsertSequenceNumber() {
            return insertSequenceNumber;
        }

        public RevCommit getEntry() {
            return entry;
        }
    }
}
