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
communicate with this sup-process to perform the git operation.
