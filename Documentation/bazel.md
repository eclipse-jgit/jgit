# Building standalone JGit with Bazel

Use these commands in a standalone JGit checkout, not inside Gerrit's pinned
submodule. Install Bazelisk to select the version in `.bazelversion`. Bazel
downloads the Java runtimes. Python 3 and the usual Unix archive tools must be
available for local actions. Maven builds are unchanged.

## Local builds and tests

```sh
bazelisk build //org.eclipse.jgit:jgit
bazelisk test //org.eclipse.jgit.test:org_eclipse_jgit_lib_ObjectIdTest
```

The checked-in Java selection also applies to the profiles below. Add
`--config=java27` to select Java 27 for local, reporting or cache-only builds.
Tests tagged `ext` require external tools and are excluded by default. Use
`--cache_test_results=no --flaky_test_attempts=1` when checking actual test
execution without cached test results or retries.

Actions use Bazel's default PATH instead of inheriting the invoking shell's
PATH. If local tools live elsewhere, define a local-only profile in
`user.bazelrc`, for example `build:local_tools --action_env=PATH=...`, and
select `--config=local_tools` for local builds. Include system tool directories
in that PATH. Do not apply host-specific paths to remote execution.

## BuildBuddy profiles

| Profile | Behavior on cache misses |
| --- | --- |
| `reporting_bb` | Publish build events; execute locally. |
| `cache_bb` | Add BuildBuddy's remote cache; execute locally. |
| `remote_bb` | Add remote execution on Linux x86-64. |

Each profile includes the preceding one. Reporting and cache-only profiles do
not select a remote platform or disable the local disk cache. They do not
cancel a remote profile selected elsewhere. Build and test commands publish
invocations; `run`, `query`, `aquery` and `cquery` do not publish by default.

Create the ignored checkout-root `user.bazelrc` with private permissions:

```sh
(umask 077; touch user.bazelrc)
chmod 600 user.bazelrc
```

Add an API key from your BuildBuddy organization, keeping the real value out
of shell history and commits:

```text
common:reporting_bb --bes_header=x-buildbuddy-api-key=YOUR_API_KEY
common:cache_bb --remote_header=x-buildbuddy-api-key=YOUR_API_KEY
common:reporting_bb --noannounce_rc
```

The profiles use the hosted service and do not enable Google credentials.
For a private instance, add endpoint overrides in the same file:

```text
common:reporting_bb --bes_backend=grpcs://YOUR_BUILDBUDDY_HOST
common:reporting_bb --bes_results_url=https://YOUR_BUILDBUDDY_HOST/invocation/
common:cache_bb --remote_cache=grpcs://YOUR_BUILDBUDDY_HOST
common:remote_bb --remote_executor=grpcs://YOUR_BUILDBUDDY_HOST
```

```sh
bazelisk test --config=reporting_bb //org.eclipse.jgit.test:org_eclipse_jgit_lib_ObjectIdTest
bazelisk test --config=cache_bb //org.eclipse.jgit.test:org_eclipse_jgit_lib_ObjectIdTest
bazelisk test --config=remote25_bb //org.eclipse.jgit.test:org_eclipse_jgit_lib_ObjectIdTest
bazelisk test --config=remote27_bb //org.eclipse.jgit.test:org_eclipse_jgit_lib_ObjectIdTest
```

`remote_bb` uses the default Java selection; `remote25_bb` and `remote27_bb`
select Java explicitly. The existing Google profiles `remote`, `remote25`,
`remote27` and `remote-cache` retain Google endpoints and application default
credentials. Do not combine Google and BuildBuddy execution profiles.

Remote execution clears the local disk cache and minimizes output downloads.
Add `--config=remote_toplevel` after the remote profile when requested artifacts
must be available locally:

```sh
bazelisk build --config=remote_bb --config=remote_toplevel //org.eclipse.jgit:jgit
```

The BuildBuddy platform inherits the digest-pinned image from the Google
platform and runs as UID/GID 1000. This lets permission-sensitive repository
tests exercise ordinary user permissions without changing Google's platform.

## Diagnostics

BuildBuddy builds and tests write `bazel-execution.log` and
`bazel-profile.json.gz` in the checkout root. Add `--config=diagnostics` to
capture the same detailed execution and timing data without BuildBuddy.
Override `--execution_log_compact_file` and `--profile` with distinct paths for
concurrent invocations or before/after comparisons.

Share invocation URLs only with people who have access. Inspect local logs
before sharing: profiles, execution logs and build-event files can include
private paths or authentication headers, even with `--noannounce_rc`.
