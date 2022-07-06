# JGit SSH support via Apache MINA sshd

This bundle provides an implementation of git transport over SSH implemented via
[Apache MINA sshd](https://mina.apache.org/sshd-project/).

## Service registration

This bundle declares a service for the `java.util.ServiceLoader` for interface
`org.eclipse.jgit.transport.ssh.SshSessionFactory`. The core JGit bundle uses the service
loader to pick up an implementation of that interface.

Note that JGit simply uses the first `SshSessionFactory` provided by the `ServiceLoader`.

If the service loader cannot find the session factory, either ensure that the service
declaration is on the Classpath of bundle `org.eclipse.jgit`, or set the factory explicitly
(see below).

In an OSGi environment, one might need a service loader bridge, or have a little OSGi
fragment for bundle `org.eclipse.jgit` that puts the right service declaration onto the
Classpath of that bundle. (OSGi fragments become part of the Classpath of their host
bundle.)

## Configuring an SSH implementation for JGit

The simplest way to set an SSH implementation for JGit is to install it globally via
`SshSessionFactory.setInstance()`. This instance will be used by JGit for all SSH
connections by default.

It is also possible to set the SSH implementation individually for any git command
that needs a transport (`TransportCommand`) via a `org.eclipse.jgit.api.TransportConfigCallback`.

To do so, set the wanted `SshSessionFactory` on the SSH transport, like:

```java
SshSessionFactory customFactory = ...; // Get it from wherever
FetchCommand fetch = git.fetch()
  .setTransportConfigCallback(transport -> {
    if (transport instanceof SshTransport) {
      ((SshTransport) transport).setSshSessionFactory(customFactory);
    }
  })
  ...
  .call();
```

## Support for SSH agents

There exist two IETF draft RFCs for communication with an SSH agent:

* an older [SSH1 protocol](https://tools.ietf.org/html/draft-ietf-secsh-agent-02) that can deal only with DSA and RSA keys, and
* a newer [SSH2 protocol](https://tools.ietf.org/html/draft-miller-ssh-agent-04) (from OpenSSH).

JGit only supports the newer OpenSSH protocol.

Communication with an SSH agent can occur over any transport protocol, and different
SSH agents may use different transports for local communication. JGit provides some
transports via the [org.eclipse.jgit.ssh.apache.agent](../org.eclipse.jgit.ssh.apache.agent/README.md)
fragment, which are discovered from `org.eclipse.jgit.ssh.apache` also via the `ServiceLoader` mechanism;
the SPI (service provider interface) is `org.eclipse.jgit.transport.sshd.agent.ConnectorFactory`.

If such a `ConnectorFactory` implementation is found, JGit may use an SSH agent. If none
is available, JGit cannot communicate with an SSH agent, and will not attempt to use one.

### SSH configurations for SSH agents

There are several SSH properties that can be used in the `~/.ssh/config` file to configure
the use of an SSH agent. For the details, see the [OpenBSD ssh-config documentation](https://man.openbsd.org/ssh_config.5).

* **AddKeysToAgent** can be set to `no`, `yes`, or `ask`. If set to `yes`, keys will be added
  to the agent if they're not yet in the agent. If set to `ask`, the user will be prompted
  before doing so, and can opt out of adding the key. JGit also supports the additional
  settings `confirm` and key lifetimes.
* **IdentityAgent** can be set to choose which SSH agent to use, if there are several running.
  It can also be set to `none` to explicitly switch off using an SSH agent at all.
* **IdentitiesOnly** if set to `yes` and an SSH agent is used, only keys from the agent that are
  also listed in an `IdentityFile` property will be considered. (It'll also switch off trying
  default key names, such as `~/.ssh/id_rsa` or `~/.ssh/id_ed25519`; only keys listed explicitly
  will be used.)

### Limitations

As mentioned above JGit only implements the newer OpenSSH protocol. OpenSSH fully implements this,
but some other SSH agents only offer partial implementations. In particular on Windows, neither
Pageant nor Win32-OpenSSH implement the `confirm` or lifetime constraints for `AddKeysToAgent`. With
such SSH agents, these settings should not be used in `~/.ssh/config`. GPG's gpg-agent can be run
with option `enable_putty_support` and can then be used as a Pageant replacement. gpg-agent appears
to support these key constraints.

OpenSSH does not implement ed448 keys, and neither does Apache MINA sshd, and hence such keys are
not supported in JGit if its built-in SSH implementation is used. ed448 or other unsupported keys
provided by an SSH agent are ignored.

## Using a different SSH implementation

To use a different SSH implementation:

* Do not include this bundle in your product.
* Include the bundle of the alternate implementation.
    * If the service loader finds the alternate implementation, nothing more is needed.
    * Otherwise ensure the service declaration from the other bundle is on the Classpath of bundle `org.eclipse.jgit`,
    * or set the `SshSessionFactory` for JGit explicitly (see above).

## Using an external SSH executable

JGit has built-in support for not using any Java SSH implementation but an external SSH
executable. To use an external SSH executable, set environment variable **GIT_SSH** to
the path of the executable. JGit will create a sub-process to run the executable and
communicate with this sub-process to perform the git operation.
