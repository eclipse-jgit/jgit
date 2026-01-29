# JGit standalone Bazel build

This directory provides a **standalone bzlmod wrapper module** used to build and
test the JGit checkout in isolation.

The repository’s `./bazelw` wrapper runs Bazel **from `tools/standalone`**, so
the JGit sources are referenced as an external module via `@jgit` (set up with a
`local_path_override`).

The main `MODULE.bazel` at the repository root stays safe to consume as a
**library dependency** (it does not call `maven.install()`), avoiding a separate
dependency universe when JGit is used from another Bazel project.

## When to use this

Use this standalone module if you are:
- developing JGit itself
- building/testing JGit in isolation

Consumers embedding JGit as a dependency should **not** use this module.

## How it works

- `tools/standalone/MODULE.bazel`:
  - declares a local override for the `jgit` module (points at the checkout)
  - defines the Maven dependency universe (`external_deps`)
  - registers toolchains needed for building JGit
- The JGit checkout is available under the repository name `@jgit`.

## Building JGit

From the repository root:

```sh
./bazelw build @jgit//...
```

## To build a specific target:

```sh
./bazelw build @jgit//:all
```

## Running tests

From the repository root:

```sh
./bazelw test @jgit//...
```

## Notes

- `./bazelw` changes directory to tools/standalone before running Bazel. Since
`tools/standalone` is not a Bazel package (no `BUILD` file), targets like
`:all` (without `@jgit//`) will fail.
- Do not add `maven.install()` to the repository root `MODULE.bazel`; that would
create a separate dependency universe when JGit is consumed as a library.
