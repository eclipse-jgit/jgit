# git forwarder

[TOC]

## Overview

### Problem statement

Large-scale git hosting environments replicate repositories across many mirror
backends to serve high clone and fetch volumes. A conventional L4 load
balancer can spread connections across those mirrors, but it has no awareness
of what is being requested. Every mirror therefore has to be prepared to serve
every project at any moment, which makes it impossible to reason about capacity
per project, pin hot repositories to warm caches, or prevent a single large
repository from crowding out others.

A per-node L7 forwarder solves this by sitting on each mirror node and
inspecting every incoming git connection before deciding where to send it.
Because it understands the repository being requested, it can enforce per-project
limits, route to the mirror most likely to have the working set warm, and
reserve capacity for high-priority projects — none of which are possible at the
network layer alone.

### Architecture

The forwarder is designed to run as a **per-node, layer-7 smart load balancer**
on every git mirror builder. Each instance handles only the connections that
arrive at its node; a lightweight load balancer in front distributes incoming
connections across nodes, so each forwarder handles roughly 1/N of total traffic
where N is the number of nodes. Capacity therefore scales horizontally by
adding nodes.

Because the forwarder sits at layer 7 and parses the git protocol, it can make
per-project routing decisions. This enables several capabilities that a
lower-level load balancer cannot provide:

- **Load isolation** — projects are kept independent so that a burst on one
  repository does not degrade others.
- **Load limiting** — per-project connection limits and timeouts prevent any
  single project from saturating a mirror.
- **Load reservations** — a mirror can be restricted to an explicit set of
  projects, enabling dedicated capacity for high-priority repositories.
- **Cache locality** — connections for the same project are consistently routed
  to the same mirror, maximizing the chance that its working set is warm.
- **Load distribution** — routing accounts for both per-mirror load and
  per-project limits, spreading work according to available capacity rather
  than round-robin.
- **Size-aware throttling** — the forwarder periodically scans project sizes
  and uses this information to bound the load that large repositories can
  place on a mirror.

In the future, this logic can be integrated to SSH/HTTP traffic as well.

### Objectives

- Inspect the very first message of an anonymous git connection to understand
  what the client wants and where it wants to go.
- Delegate the routing decision to a pluggable policy, so that different
  deployments can implement arbitrary logic (fixed destination, repository
  sharding, access control, etc.).
- Forward the full session transparently.

### Description

The forwarder acts as a transparent TCP proxy specialized for the anonymous git
protocol. When a client connects, the forwarder peeks at the first protocol
message to extract routing metadata — the command, repository path, and host —
then immediately hands the connection off to an upstream server. From that
point on, it copies bytes in both directions without interpreting them, behaving
like a pipe.

The routing decision is made by a listener that receives the parsed request
metadata and returns an upstream address. The listener can also observe
connection lifecycle events (open, close, error) for logging or metrics.

### Routing

The routing decision is separated from the forwarding mechanism through a
listener interface. When a client connects, the forwarder presents the parsed
request to the listener and waits for a response. The response is simply a
destination address. Returning no destination causes the connection to be
dropped silently.

This separation makes it possible to implement a wide range of policies without
changing the forwarding core. The listener also receives callbacks when a
connection closes or when an error occurs, enabling cleanup.

### Bidirectional proxying

Once an upstream connection is established, the forwarder copies data in both
directions concurrently: client to upstream and upstream to client. Both
directions run in parallel, and the session ends when either side closes its end
of the connection.

### Configuration

The CLI command reads a simple config file with two required settings: the
address to listen on and the upstream address to forward to. Both accept a
`host:port` pair.

```
[global]
    listen = 0.0.0.0:9418
    remote = backend-host:9418
```

### Usage

```
jgit forwarder --config-file /path/to/forwarder.conf
```

The process listens until it receives a shutdown signal, at which point it
stops accepting new connections and waits briefly for in-flight sessions to
finish.
