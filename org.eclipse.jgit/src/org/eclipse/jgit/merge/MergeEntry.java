package org.eclipse.jgit.merge;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;

public class MergeEntry {

  byte[] path;
  String pathString;
  FileMode fileMode;
  int rawFileMode;
  ObjectId objectId;
  public MergeEntry(byte[] path,
      String pathString,
      FileMode fileMode,
      int rawFileMode,
      ObjectId objectId) {
    this.path = path;
    this.pathString = pathString;
    this.fileMode = fileMode;
    this.objectId = objectId;
    this.rawFileMode = rawFileMode;
  }

}
