# JGit SSH support via JSch

This bundle provides an implementation of git transport over SSH implemented via JSch.

**This bundle should be considered deprecated**. It is essentially unmaintained, and
the JGit project may decide anytime to remove it completely without further ado.

The officially supported SSH transport is in bundle `org.eclipse.jgit.ssh.apache` and is
built upon [Apache MINA sshd](https://mina.apache.org/sshd-project/).

## Service registration

This bundle declares a service for the `java.util.ServiceLoader` for interface
`org.eclipse.jgit.transport.ssh.SshSessionFactory`. The core JGit bundle uses the service
loader to pick up an implementation of that interface. The bundle in an OSGi fragment
to ensure that the service loader works in an OSGi environment without the need to
install a service loader bridge.

Note that JGit simply uses the first `SshSessionFactory` provided by the `ServiceLoader`.

## Using a different SSH implementation

To use a different SSH implementation:

* Do not include this bundle in your product.
* Include the bundle of the alternate implementation.
    * If the service loader finds the alternate implementation, nothing more is needed.
    * Otherwise ensure the service declaration from the other bundle is on the Classpath of bundle `org.eclipse.jgit`,
    * or set the `SshSessionFactory` for JGit explicitly (see below).

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

## Using an external SSH executable

JGit has built-in support for not using any Java SSH implementation but an external SSH
executable. To use an external SSH executable, set environment variable **GIT_SSH** to
the path of the executable. JGit will create a sub-process to run the executable and
communicate with this sub-process to perform the git operation.
