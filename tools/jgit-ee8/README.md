# JGit EE8 Bazel bridge

This package contains the Bazel-only bridge for source consumers that still run
on EE8 and `javax.servlet` while JGit master uses `jakarta.servlet`.

JGit hosts this bridge because Gerrit is a known downstream that consumes JGit
from source through a git submodule and still runs Jetty 12 EE8. Keeping the
bridge in JGit lets Gerrit track JGit master and avoids maintaining a long-lived
servlet-4 branch just for Gerrit. That branch was never a stable JGit release
line, so it is not a sustainable way for Gerrit to receive JGit maintenance
updates.

The bridge intentionally keeps the original JGit package names. It rewrites only
servlet API references from `jakarta.servlet` to `javax.servlet`, then compiles
generated jars from the rewritten source jars.

Generated targets:

* `//org.eclipse.jgit.http.server.ee8:jgit-servlet-ee8`
* `//org.eclipse.jgit.lfs.server.ee8:jgit-lfs-server-ee8`

Do not put one of these generated jars and its canonical Jakarta counterpart on
the same classpath. They contain the same JGit classes.

This is intentionally limited to Bazel source consumers. It does not add Maven,
Tycho, p2, or Maven Central publication machinery. Those should be added only if
a non-Bazel consumer needs published EE8 artifacts.

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

Next steps:

* Keep this source-consumer bridge while Gerrit runs on Jetty 12 EE8.
* Reuse these targets for other Bazel source consumers that need EE8 output.
* Add Maven/Tycho/p2 generation only when a non-Bazel consumer needs published
  EE8 artifacts.
* Keep the canonical `srcs` filegroups aligned with each servlet-facing
  `java_library` so new Java sources are transformed automatically.
