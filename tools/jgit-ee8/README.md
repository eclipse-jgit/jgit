# JGit EE8 Bazel bridge

This package contains the Bazel-only bridge for source consumers that still run
on EE8 and `javax.servlet` while this branch uses `jakarta.servlet`.

The stable-7.4 branch keeps this bridge intentionally small. It rewrites only
servlet API references from `jakarta.servlet` to `javax.servlet` and compiles
generated jars from the rewritten source jars. It does not add the Eclipse
Transformer dependency tree used by newer branches.

Generated targets:

* `//org.eclipse.jgit.http.server.ee8:jgit-servlet-ee8`
* `//org.eclipse.jgit.lfs.server.ee8:jgit-lfs-server-ee8`

Do not put one of these generated jars and its canonical Jakarta counterpart on
the same classpath. They contain the same JGit classes.

Run:

```sh
bazelisk test //tools/jgit-ee8:generated_srcs_test
```

The test checks:

* generated sources are derived from the canonical source filegroups
* generated srcjar entries use Java package paths
* generated sources preserve line counts for debugger breakpoints
* generated sources contain `javax.servlet`, not `jakarta.servlet`
* generated sources do not move JGit classes to an `.ee8` package
