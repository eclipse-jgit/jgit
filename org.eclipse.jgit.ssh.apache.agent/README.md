# JGit SSH agent transport support for Apache MINA sshd

This bundle provides optional support for communicating with an SSH agent for SSH or SFTP authentication.
It is an OSGi fragment for bundle [org.eclipse.jgit.ssh.apache](../org.eclipse.jgit.ssh.apache/README.md),
and it provides transports for local communication with SSH agents.

## Supported SSH agent transports

### Linux, OS X, BSD

On Linux, OS X, and BSD, the only transport mechanism supported is the usual communication via a Unix
domain socket. This is the only protocol the OpenSSH SSH agent supports. A Unix domain socket appears
as a special file in the file system; this file name is typically available in the environment variable
`SSH_AUTH_SOCK`.

The SSH config `IdentityAgent` can be set to this socket filename to specify exactly which Unix domain
socket to use, or it can be set to `SSH_AUTH_SOCK` to use the value from that environment variable. If
`IdentityAgent` is not set at all, JGit uses `SSH_AUTH_SOCK` by default. If the variable is not set, no
SSH agent will be used. `IdentityAgent` can also be set to `none` to not use any SSH agent.

### Windows

On Windows, two different transports are supported:

* A transport over a Windows named pipe. This is used by Win32-OpenSSH, and is available for Pageant since version 0.75.
* A Pageant-specific legacy transport via shared memory; useful for Pageant and GPG's gpg-agent.

Possible settings of `IdentityAgent` to select a particular transport are

* `//./pipe/openssh-ssh-agent`: the Windows named pipe of Win32-OpenSSH.
* `//./pageant`: the pageant-specific shared-memory mechanism.
* `none`: do not use any SSH agent.

The default transport on Windows if `IdentityAgent`is not set at all is the Pageant shared-memory transport.
Environment variable `SSH_AUTH_SOCK` needs not be set for Pageant, and _must not_ be set for Win32-OpenSSH.

It is also possible to use a named pipe as transport for Pageant. Unfortunately, Pageant unnecessarily
cryptographically obfuscates the pipe name, so it is not possible for JGit to determine it automatically.
The pipe name is `pageant.<user name>.<sha256>`, for instance `pageant.myself.c5687736ba755a70b000955cb191698aed7db221c2b0710199eb1f5298922ab5`.
A user can look up the name by starting Pageant and then running `dir \\.\pipe\\` in a command shell.
Once the name is known, setting `IdentityAgent` to the pipe name as `//./pipe/pageant.myself.c5687736ba755a70b000955cb191698aed7db221c2b0710199eb1f5298922ab5`
makes JGit use this Windows named pipe for communication with Pageant.

(You can use forward slashes in the `~/.ssh/config` file. SSH config file parsing has its own rules about
backslashes in config files; which are treated as escape characters in some contexts. With backslashes
one would have to write, e.g., `\\\\.\pipe\openssh-ssh-agent`.)

With these two transport mechanisms, Pageant and Win32-OpenSSH are supported. As for GPG: the gpg-agent
can be configured to emulate ssh-agent (presumably via a WinSockets2 "Unix domain socket" on Windows) or
to emulate Pageant (using the shared memory mechanism). Running gpg-agent with the `enable-ssh-support`
option is [reported not to work on Windows](https://dev.gnupg.org/T3883), though. But the PuTTY emulation
in gpg-agent _should_ work, so it should be possible to use gpg-agent instead of Pageant.

Neither Pageant (as of version 0.76) nor Win32-OpenSSH (as of version 8.6) support the `confirm` or lifetime
constraints for `AddKeysToAgent`. gpg-agent apparently does, even when communicating over the Pageant shared
memory mechanism.

The ssh-agent from git bash is currently not supported. It would need a connector handling Cygwin socket
files and the Cygwin handshake over a TCP stream socket bound to the loopback interface. The Cygwin socket
file _is_ exposed in the Windows file system at %TEMP%\ssh-XXXXXXXXXX\agent.&lt;number>, but it does not
have a fixed name (the X's and the number are variable and change each time ssh-agent is started).

## Implementation

The implementation of all transports uses JNA.