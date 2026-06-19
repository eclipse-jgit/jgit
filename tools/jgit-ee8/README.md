# JGit EE8 Bazel bridge

This package contains the Bazel-only bridge for source consumers that still run
on EE8 and `javax.servlet` while JGit master uses `jakarta.servlet`.

There is also a separate Maven EE8 parent at `org.eclipse.jgit.ee8/`. It
exists to keep Bazel and Maven aligned for source generation, build, and test.
TODO: add Tycho/p2 publishing as a follow-up packaging step.

Run Maven tests from the repo root:

```sh
mvn test
```

That is the supported path. The standalone `org.eclipse.jgit.ee8` reactor is
only useful if the canonical JGit artifacts it depends on are already built or
installed locally.

To run only the EE8 Maven modules, use the reactor selector from the repo root:

```sh
mvn -pl org.eclipse.jgit.ee8 -am test
```

That keeps the EE8 build scoped to the EE8 reactor while still building the
required upstream modules.

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

This is intentionally limited to source generation, build, and test. It does
not add Tycho, p2, or Maven Central publication machinery yet.

Run:

```sh
bazelisk test \
  //tools/jgit-ee8:generated_srcs_test \
  //org.eclipse.jgit.junit.http.ee8:tests
```

`//tools/jgit-ee8:generated_srcs_test` checks:

* generated sources are derived from the canonical source filegroups
* generated srcjar entries use Java package paths
* generated sources preserve line counts for debugger breakpoints
* generated sources contain `javax.servlet`, not `jakarta.servlet`
* generated sources do not move JGit classes to an `.ee8` package

The dedicated `org.eclipse.jgit.*.ee8` targets follow the Jetty 12 EE8 testing
model in a smaller form. Jetty generates EE8 main and test sources from the
canonical newer-EE modules and then runs the EE8 tests in the EE8 module graph.
JGit does the same for the servlet-facing tests: the HTTP/LFS test sources and
shared `junit-http` helpers are transformed to `javax.servlet`, wired to Jetty
EE8 test dependencies, and run against the generated EE8 JGit jars.

`//org.eclipse.jgit.http.test.ee8:server_unit` is a fast, focused target for the
three HTTP server utility tests. It is not part of the default EE8 suite because
those classes already run under `//org.eclipse.jgit.http.test.ee8:http`.

The test rewrite rules also map Jetty test helper imports from
`org.eclipse.jetty.ee10.servlet` to the Jetty EE8 packages needed by the tests.
They are intentionally test-only and separate from the production
`jakarta.servlet` to `javax.servlet` rules.

Next steps:

* Keep this source-consumer bridge while Gerrit runs on Jetty 12 EE8.
* Reuse these targets for other Bazel source consumers that need EE8 output.
* Move the generated-test JUnit helper to bazlets only if more generated-test
  users need the same split between suite source labels and compiled sources.
* TODO: add Maven/Tycho/p2 generation when a non-Bazel consumer needs published
  EE8 artifacts.
* Keep the canonical `srcs` filegroups aligned with each servlet-facing
  `java_library` so new Java sources are transformed automatically.
