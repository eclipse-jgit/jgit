Java Git
========

An implementation of the Git version control system in pure Java.

This package is licensed under the EDL (Eclipse Distribution
License).

- org.eclipse.jgit

    A pure Java library capable of being run standalone, with no
    additional support libraries. It provides classes to read and
    write a Git repository and operate on a working directory.

    All portions of jgit are covered by the EDL. Absolutely no GPL,
    LGPL or EPL contributions are accepted within this package.

- org.eclipse.jgit.ant

    Ant tasks based on JGit.

- org.eclipse.jgit.http.server

    Server for the smart and dumb Git HTTP protocol.

- org.eclipse.jgit.pgm

    Command-line interface Git commands implemented using JGit
    ("pgm" stands for program).

- org.eclipse.jgit.test

    Unit tests for org.eclipse.jgit and the same licensing rules.


Warnings/Caveats
----------------

- Symbolic links are not supported because java does not support it.
  Such links could be damaged.

- Only the timestamp of the index is used by jgit check if  the index
  is dirty.

- Don't try the library with a JDK other than 1.6 (Java 6) unless you
  are prepared to investigate problems yourself. JDK 1.5.0_11 and later
  Java 5 versions *may* work. Earlier versions do not. JDK 1.4 is *not*
  supported. Apple's Java 1.5.0_07 is reported to work acceptably. We
  have no information about other vendors. Please report your findings
  if you try.

- CRLF conversion is performed depending on the core.autocrlf setting,
  however Git for Windows by default stores that setting during
  installation in the "system wide" configuration file. If Git is not
  installed, use the global or repository configuration for the
  core.autocrlf setting.

- The system wide configuration file is located relative to where C
  Git is installed. Make sure Git can be found via the PATH
  environment variable. When installing Git for Windows check the "Run
  Git from the Windows Command Prompt" option. There are other options
  like the jgit.gitprefix system propety or Eclipse settings that can
  be used for pointing out where C Git is installed. Modifying PATH is
  the recommended option if C Git is installed.

- We try to use the same notation of $HOME as C Git does. On Windows
  this is often not same value as the user.home system property.


Package Features
----------------

- org.eclipse.jgit/

    * Read loose and packed commits, trees, blobs, including
      deltafied objects.

    * Read objects from shared repositories

    * Write loose commits, trees, blobs.

    * Write blobs from local files or Java InputStreams.

    * Read blobs as Java InputStreams.

    * Copy trees to local directory, or local directory to a tree.

    * Lazily loads objects as necessary.

    * Read and write .git/config files.

    * Create a new repository.

    * Read and write refs, including walking through symrefs.

    * Read, update and write the Git index.

    * Checkout in dirty working directory if trivial.

    * Walk the history from a given set of commits looking for commits
      introducing changes in files under a specified path.

    * Object transport
      Fetch via ssh, git, http, Amazon S3 and bundles.
      Push via ssh, git and Amazon S3. JGit does not yet deltify
      the pushed packs so they may be a lot larger than C Git packs.

- org.eclipse.jgit.pgm/

    * Assorted set of command line utilities. Mostly for ad-hoc testing of jgit
      log, glog, fetch etc.


Missing Features
----------------

There are some missing features:

- gitattributes support

- Recursive merge strategy


Support
-------

Post question, comments or patches to the jgit-dev@eclipse.org mailing list.
You need to be subscribed to post, see here:

https://dev.eclipse.org/mailman/listinfo/jgit-dev


Contributing
------------

See the EGit Contributor Guide:

http://wiki.eclipse.org/EGit/Contributor_Guide


About Git
---------

More information about Git, its repository format, and the canonical
C based implementation can be obtained from the Git website:

http://git-scm.com/
