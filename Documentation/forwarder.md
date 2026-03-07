# JGit Forwarder

The JGit forwarder is a proxy for the **anonymous git protocol** (`git://`). It listens for incoming git protocol connections, parses the first pkt-line to obtain request metadata (e.g. project path and command), and forwards traffic to a fixed upstream server. Optionally it can enforce **global and per-project concurrency limits**; when a limit is exceeded the client receives a protocol error message instead of being forwarded.

## Use case

- Expose a single entry point (host:port) that proxies to an upstream git daemon.
- Apply concurrency limits so that one project or the whole forwarder cannot exceed a maximum number of concurrent connections (e.g. to protect the upstream server or enforce quotas).
- Reject overload with a clear error (e.g. `global maxStart exceeded` or `project <pattern> maxStart exceeded`) so clients see a reason instead of a hang or connection reset.

The forwarder uses plain TCP (no TLS). It is intended for use in controlled or private networks.

## Running the forwarder

```bash
jgit forwarder --config-file /path/to/forwarder.conf [--pid-file /path/to/pidfile]
```

| Option | Required | Default | Description |
|--------|----------|---------|-------------|
| `--config-file` | Yes | — | Path to the forwarder configuration file (git-config format). |
| `--pid-file` | No | `jgit-forwarder.pid` (current directory) | File to which the process ID of the forwarder is written. |

The process writes its PID to the pid file, then listens and forwards until it receives a shutdown signal. Startup messages (listen address and upstream address) are printed to stderr.

## Configuration file

The configuration file uses the standard [git config](https://git-scm.com/docs/git-config) format. All forwarder settings live under a **`[global]`** section; per-project limits use **`[REPO "<pattern>"]`** sections.

### Global section: `[global]`

| Key | Required | Description |
|-----|----------|-------------|
| `listen` | Yes | Address and port to bind. Accepts `host`, `host:port`, or `0.0.0.0:port`. If port is omitted, the default git daemon port (9418) is used. |
| `remote` | Yes | Upstream address and port to forward connections to. Same format as `listen`; default port when omitted is 9419. |
| `maxStart` | No | If set to a positive integer, limits the total number of concurrent forwarded connections across all projects. When exceeded, new connections are rejected with the error `global maxStart exceeded`. If unset or &le; 0, no global limit is applied. |

**Example:**

```ini
[global]
    listen = 0.0.0.0:9418
    remote = 127.0.0.1:9419
    maxStart = 100
```

### Per-project limits: `[repo "<pattern>"]`

You can add optional **per-project** concurrency limits. Each section defines a Java-style regex **pattern** (matched against the project path from the first pkt-line) and a **maxStart** value. Only the **first matching** `[repo "..."]` section applies per request.

| Key | Required | Description |
|-----|----------|-------------|
| `maxStart` | Yes (for the section to apply) | Maximum concurrent forwarded connections for repos whose path matches the section’s pattern. Must be &gt; 0. When exceeded, the client receives `repo <pattern> maxStart exceeded`. |

The repo path is normalized (e.g. leading slashes removed); the `.git` suffix is not required in the pattern.

**Example:**

```ini
[global]
    listen = 127.0.0.1:9418
    remote = 127.0.0.1:9419
    maxStart = 200

[REPO "myteam/.*"]
    maxStart = 20

[REPO "legacy/repo"]
    maxStart = 5
```

Here, connections to `myteam/foo` or `myteam/bar` are limited to 20 concurrent; `legacy/repo` to 5; all others only see the global limit of 200.

## Rejection behavior

When the forwarder rejects a connection (e.g. due to a limit), it sends a **pkt-line error** to the client with the corresponding message. The client will see that error instead of being proxied. Limits are released when the connection closes (or when opening the upstream connection fails), so the next request can acquire the slot.