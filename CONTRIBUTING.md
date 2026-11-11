# Contributing

Please refer to the [contributor guide](https://wiki.eclipse.org/EGit/Contributor_Guide) for all the details.
Contributions require that you sign the [Eclipse Contributor Agreement](https://www.eclipse.org/legal/ECA.php).

## Reporting bugs

For anything other than small changes, it's a good idea to open a bug
report for it (in case one doesn't already exist). This gives others the
chance to give input and is useful for tracking. 
[Open JGit issues on GitHub](https://github.com/eclipse-jgit/jgit/issues).

Older bugs can be found [in Bugzilla](https://bugs.eclipse.org/bugs/buglist.cgi?bug_status=__open__&component=JGit&list_id=21379030&product=JGit).

## Submitting changes

- We use [Gerrit on GerritHub](https://eclipse.gerrithub.io/q/project:eclipse-jgit/jgit+status:open)
  to review all changes by committers or contributors before they are merged.
- Make sure you have an account and have set up the `commit-msg` hook
before committing.
- When committing your changes, see the contributor guide or other commits
on what your commit message should include.
- Run the following to push your change for review (with `username`
replaced by your GitHub username):

```bash
git push ssh://username@eclipse.gerrithub.io:29418/eclipse-jgit/jgit HEAD:refs/for/master
```

- Add the link to the review as a comment on the bug report, so that
people coming from the bug report can find it.
- Then wait for someone to review your change. If there is something to be
corrected, amend your commit and push it again.

Have fun :).

## Java formatting

The standalone JDT formatter uses the checked-in Eclipse profile at
`tools/eclipse-JGit-Format.xml`. From the repository root, pass the Java files
to check or format explicitly:

```bash
tools/format-java --check org.eclipse.jgit/src/org/eclipse/jgit/api/Git.java
tools/format-java --write org.eclipse.jgit/src/org/eclipse/jgit/api/Git.java
```

`--check` never modifies files and exits with status 1 if a selected file needs
formatting. `--write` updates only the explicitly named files.

Bazel can run the formatter directly without a wrapper or an explicit profile:

```bash
bazel run //tools/formatter:formatter -- --check path/to/File.java
bazel run format -- --check path/to/File.java     # from the repository root
bazel run //:format -- --write path/to/File.java  # from any workspace directory
```

Relative file paths are resolved from the invocation directory. The default
profile is resolved from Bazel's workspace directory. `tools/format-java` is a
convenience wrapper; it does not change the working directory, so run it within
the workspace (using its absolute path from a subdirectory if needed).

To limit formatting to lines changed against a Git base, add
`--changed-lines <base>`:

```bash
tools/format-java --check --changed-lines origin/master \
  org.eclipse.jgit/src/org/eclipse/jgit/api/Git.java
```

The changed-line mode uses Git's zero-context diff ranges (including staged and
unstaged changes); an explicitly named untracked Java file is treated as
entirely new. The JDT formatter may adjust nearby lines to preserve valid
formatting.
