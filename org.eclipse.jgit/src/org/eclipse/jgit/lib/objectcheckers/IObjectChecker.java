package org.eclipse.jgit.lib.objectcheckers;

import org.eclipse.jgit.errors.CorruptObjectException;

public interface IObjectChecker {

    void check(byte[] raw) throws CorruptObjectException;

}
