# Java Git

An implementation of the Git version control system in pure Java.

This project is licensed under the __EDL__ (Eclipse Distribution
License).

JGit can be imported straight into Eclipse and built and tested from
there. It can be built from the command line using
[Maven](https://maven.apache.org/) or [Bazel](https://bazel.build/).
The CI builds use Maven and run on [Jenkins](https://ci.eclipse.org/jgit/).

- __org.eclipse.jgit__

    A pure Java library capable of being run standalone, with no
    additional support libraries. It provides classes to read and
    write a Git repository and operate on a working directory.

    All portions of JGit are covered by the EDL. Absolutely no GPL,
    LGPL or EPL contributions are accepted within this package.

- __org.eclipse.jgit.ant__

    Ant tasks based on JGit.

- __org.eclipse.jgit.archive__

    Support for exporting to various archive formats (zip etc).

- __org.eclipse.jgit.http.apache__

    [Apache httpclient](https://hc.apache.org/httpcomponents-client-ga/) support.

- __org.eclipse.jgit.http.server__

    Server for the smart and dumb
    [Git HTTP protocol](https://github.com/git/git/blob/master/Documentation/technical/http-protocol.txt).

- __org.eclipse.jgit.lfs__

    Support for [LFS](https://git-lfs.github.com/) (Large File Storage).

- __org.eclipse.jgit.lfs.server__

    Basic LFS server support.

- __org.eclipse.jgit.packaging__

    Production of Eclipse features and p2 repository for JGit. See the JGit
    Wiki on why and how to use this module.

- __org.eclipse.jgit.pgm__

    Command-line interface Git commands implemented using JGit
    ("pgm" stands for program).

- __org.eclipse.jgit.ssh.apache__

    Client support for the ssh protocol based on
    [Apache Mina sshd](https://mina.apache.org/sshd-project/).

- __org.eclipse.jgit.ui__

    Simple UI for displaying git log.

## Tests

- __org.eclipse.jgit.junit__, __org.eclipse.jgit.junit.http__,
__org.eclipse.jgit.junit.ssh__: Helpers for unit testing
- __org.eclipse.jgit.ant.test__: Unit tests for org.eclipse.jgit.ant
- __org.eclipse.jgit.http.test__: Unit tests for org.eclipse.jgit.http.server
- __org.eclipse.jgit.lfs.server.test__: Unit tests for org.eclipse.jgit.lfs.server
- __org.eclipse.jgit.lfs.test__: Unit tests for org.eclipse.jgit.lfs
- __org.eclipse.jgit.pgm.test__: Unit tests for org.eclipse.jgit.pgm
- __org.eclipse.jgit.ssh.apache.test__: Unit tests for org.eclipse.jgit.ssh.apache
- __org.eclipse.jgit.test__: Unit tests for org.eclipse.jgit

## Warnings/Caveats

- Native symbolic links are supported, provided the file system supports
  them. For Windows you must use a non-administrator account and have the SeCreateSymbolicLinkPrivilege.

- Only the timestamp of the index is used by JGit if the index is
  dirty.

- JGit requires at least a Java 8 JDK.

- CRLF conversion is performed depending on the `core.autocrlf` setting,
  however Git for Windows by default stores that setting during
  installation in the "system wide" configuration file. If Git is not
  installed, use the global or repository configuration for the
  core.autocrlf setting.

- The system wide configuration file is located relative to where C
  Git is installed. Make sure Git can be found via the PATH
  environment variable. When installing Git for Windows check the "Run
  Git from the Windows Command Prompt" option. There are other options
  like Eclipse settings that can be used for pointing out where C Git
  is installed. Modifying PATH is the recommended option if C Git is
  installed.

- We try to use the same notation of `$HOME` as C Git does. On Windows
  this is often not the same value as the `user.home` system property.

## Features

- __org.eclipse.jgit__
  - Read loose and packed commits, trees, blobs, including
    deltafied objects.
  - Read objects from shared repositories
  - Write loose commits, trees, blobs.
  - Write blobs from local files or Java InputStreams.
  - Read blobs as Java InputStreams.
  - Copy trees to local directory, or local directory to a tree.
  - Lazily loads objects as necessary.
  - Read and write .git/config files.
  - Create a new repository.
  - Read and write refs, including walking through symrefs.
  - Read, update and write the Git index.
  - Checkout in dirty working directory if trivial.
  - Walk the history from a given set of commits looking for commits
      introducing changes in files under a specified path.
  - Object transport

      Fetch via ssh, git, http, Amazon S3 and bundles.
      Push via ssh, git and Amazon S3. JGit does not yet deltify
      the pushed packs so they may be a lot larger than C Git packs.

  - Garbage collection
  - Merge
  - Rebase
  - And much more

- __org.eclipse.jgit.pgm__
  - Assorted set of command line utilities. Mostly for ad-hoc testing of jgit
      log, glog, fetch etc.
- __org.eclipse.jgit.ant__
  - Ant tasks
- __org.eclipse.jgit.archive__
  - Support for Zip/Tar and other formats
- __org.eclipse.http__
  - HTTP client and server support

## Missing Features

There are some missing features:

- verifying signed commits
- signing tags
- signing push

## Support

Post questions, comments or discussions to the jgit-dev@eclipse.org mailing list.
You need to be [subscribed](https://dev.eclipse.org/mailman/listinfo/jgit-dev)
to post. File bugs and enhancement requests in
[Bugzilla](https://wiki.eclipse.org/EGit/Contributor_Guide#Filing_Bugs).

## Contributing

See the [EGit Contributor Guide](http://wiki.eclipse.org/EGit/Contributor_Guide).

## About Git

More information about Git, its repository format, and the canonical
C based implementation can be obtained from the
[Git website](http://git-scm.com/).
