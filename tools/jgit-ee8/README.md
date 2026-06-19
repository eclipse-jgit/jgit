# JGit EE8 Bazel bridge

This package contains the Bazel-only bridge for source consumers that still run
on EE8 and `javax.servlet` while JGit master uses `jakarta.servlet`.

There is also a separate Maven EE8 parent at `org.eclipse.jgit.ee8/`. It
exists to keep Bazel and Maven aligned for source generation, build, and test.

Each Maven EE8 module carries full OSGi metadata (`META-INF/MANIFEST.MF`,
`build.properties`, and, for the library bundles, `OSGI-INF/l10n/plugin.properties`
and `META-INF/SOURCE-MANIFEST.MF`), so every EE8 jar is a valid OSGi bundle just
like the canonical JGit jars. They are deliberately **not** added to the JGit
features, the `org.eclipse.jgit.repository` p2 update site, or the Tycho target
platform. See "Why the EE8 bundles are not published to Tycho/p2" below.

Run Maven tests from the repo root:

```sh
mvn test
```

That is the supported path. The standalone `org.eclipse.jgit.ee8` reactor is
only useful if the canonical JGit artifacts it depends on are already built or
installed locally.

To run only the EE8 Maven modules once the canonical JGit artifacts are
installed (for example after `mvn -DskipTests install`), drive the EE8 reactor
directly:

```sh
mvn -f org.eclipse.jgit.ee8/pom.xml test
```

Note: `mvn -pl org.eclipse.jgit.ee8 -am test` does not work — Maven `-pl` does
not descend into a selected aggregator's modules, so only the EE8 parent pom
would build and none of the EE8 modules would be tested.

## Why the EE8 bundles are not published to Tycho/p2

The EE8 jars are valid OSGi bundles, but they are intentionally kept out of the
JGit feature set, the `org.eclipse.jgit.repository` p2 update site, and the
target platform. Publishing them to p2 would be a contradiction:

* **They collide with the canonical bundles.** The EE8 bundles keep the same
  exported packages as the canonical ones (`org.eclipse.jgit.http.server`,
  `org.eclipse.jgit.lfs.server`, `org.eclipse.jgit.junit.http`) and differ only
  in the servlet API (`javax.servlet` 4.x and Jetty EE8 instead of
  `jakarta.servlet` 6.x and Jetty EE10). An EE8 bundle and its canonical
  counterpart must never sit on the same classpath — they contain the same JGit
  classes. A p2 update site is precisely a place where features are meant to be
  co-installable, so offering both there invites exactly the conflict this
  bridge forbids.
* **They would not resolve in a normal IDE anyway.** The EE8 bundles import
  `javax.servlet [4.0.0,5.0.0)`, which a modern `jakarta.servlet` Eclipse does
  not provide, so the feature would generally fail to resolve when installed.
* **No consumer installs them from p2.** The only consumer is Gerrit, which
  builds JGit from source (Bazel) or consumes the Maven jars. It never installs
  JGit from the Eclipse p2 update site, so p2 publication would serve nobody.

Keeping the OSGi manifests (so the jars are proper bundles) while staying out of
the published feature/repository/target-platform gives EE8 source consumers a
clean bundle without polluting the JGit update site or risking the co-install
conflict above.

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

The Bazel bridge is intentionally limited to source generation, build, and
test. The Maven EE8 jars are OSGi bundles, but neither Bazel nor Maven publishes
the EE8 artifacts to Tycho/p2 or Maven Central, by design (see "Why the EE8
bundles are not published to Tycho/p2" above).

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
* Do not publish the EE8 artifacts to Tycho/p2; they would collide with the
  canonical bundles in any shared update site (see the section above).
* Keep the canonical `srcs` filegroups aligned with each servlet-facing
  `java_library` so new Java sources are transformed automatically.
