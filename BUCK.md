Java Git - Building with Buck
=============================


Installation
------------

There is currently no binary distribution of Buck, so it has to be manually
built and installed.  Apache Ant is required.  Currently only Linux and Mac
OS are supported.

Clone the git and build it:

----
  git clone https://gerrit.googlesource.com/buck
  cd buck
  ant
----

Make sure you have a `bin/` directory in your home directory and that
it is included in your path:

----
  mkdir ~/bin
  PATH=~/bin:$PATH
----

Add a symbolic link in `~/bin` to the buck executable:

----
  ln -s `pwd`/bin/buck ~/bin/
----

Verify that `buck` is accessible:

----
  which buck
----

If you plan to use the link:#buck-daemon[Buck daemon] add a symbolic
link in `~/bin` to the buckd executable:

----
  ln -s `pwd`/bin/buckd ~/bin/
----

To enable autocompletion of buck commands, install the autocompletion
script from `./scripts/bash_completion` in the buck project.  Refer to
the script's header comments for installation instructions.


[[eclipse]]
Eclipse Integration
-------------------


Generating the Eclipse Project
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Create the Eclipse project:

----
  tools/project.py
----

In Eclipse, choose 'Import existing project' and select the `jgit` project
from the current working directory.

Expand the `jgit` project, right-click on the `buck-out` folder, select
'Properties', and then under 'Attributes' check 'Derived'.

Note that if you make any changes in the project configuration
that get saved to the `.project` file, for example adding Resource
Filters on a folder, they will be overwritten the next time you run
`tools/project.py`.


Refreshing the Classpath
~~~~~~~~~~~~~~~~~~~~~~~~

If an updated classpath is needed, the Eclipse project can be
refreshed and missing dependency JARs can be downloaded:

----
  tools/project.py
----


Attaching Sources
~~~~~~~~~~~~~~~~~

To save time and bandwidth source JARs are only downloaded by the buck
build where necessary to compile Java source into JavaScript using the
GWT compiler.  Additional sources may be obtained, allowing Eclipse to
show documentation or dive into the implementation of a library JAR:

----
  tools/project.py --src
----


[[build]]
Building on the Command Line
----------------------------

Build all
~~~~~~~~~

To build all artifacts except bundles

----
  buck build all
----

The output artifacts will be placed project specific directories under:

----
  buck-out/gen/<project>
----

Build bundles
~~~~~~~~~~~~~

To build bundles

----
  buck build bundles
----

The output artifacts will be placed project specific directories, e. g.:

----
  buck-out/gen/org.eclipse.jgit/bundle.jar
----

[[tests]]
Running Unit Tests
------------------

To run all tests:

----
  buck test --all
----

To run a specific test
`org.eclipse.jgit.internal.storage.file.GcBasicPackingTest`:

----
  buck test buck test //org.eclipse.jgit.test:internal.storage.file.GcBasicPackingTest
----


Dependencies
------------

Dependency JARs are normally downloaded automatically, but Buck can inspect
its graph and download any missing JAR files.  This is useful to enable
subsequent builds to run without network access:

----
  tools/download_all.py
----

When downloading from behind a proxy (which is common in some corporate
environments), it might be necessary to explicitly specify the proxy that
is then used by `curl`:

----
  export http_proxy=http://<proxy_user_id>:<proxy_password>@<proxy_server>:<proxy_port>
----

Redirection to local mirrors of Maven Central is supported by defining
specific properties in `local.properties`, a file that is not tracked by Git:

----
  echo download.MAVEN_CENTRAL = http://nexus.my-company.com/ >>local.properties
----

The `local.properties` file may be placed in the root of the jgit repository
being built, or in `~/.gerritcodereview/`.  The file in the root of the jgit
repository has precedence.

Caching Build Results
~~~~~~~~~~~~~~~~~~~~~

Build results can be locally cached, saving rebuild time when
switching between Git branches. Buck's documentation covers
caching in link:http://facebook.github.io/buck/concept/buckconfig.html[buckconfig].
The trivial case using a local directory is:

----
  cat >.buckconfig.local <<EOF
  [cache]
    mode = dir
    dir = buck-cache
  EOF
----

[[buck-daemon]]
Using Buck daemon
~~~~~~~~~~~~~~~~~

Buck ships with a daemon command `buckd`, which uses the
link:https://github.com/martylamb/nailgun[Nailgun] protocol for running
Java programs from the command line without incurring the JVM startup
overhead.

Using a Buck daemon can save significant amounts of time as it avoids the
overhead of starting a Java virtual machine, loading the buck class files
and parsing the build files for each command.

It is safe to run several buck daemons started from different project
directories and they will not interfere with each other. Buck's documentation
covers daemon in http://facebook.github.io/buck/command/buckd.html[buckd].

The trivial use case is to run `buckd` from the project's root directory and
run `buck` as usual:

----
  buckd
  buck build all
  Using buckd.
  [-] PARSING BUILD FILES...FINISHED 0.6s
  [-] BUILDING...FINISHED 0.2s
----

Override Buck's settings
~~~~~~~~~~~~~~~~~~~~~~~~

Additional JVM args for Buck can be set in `.buckjavaargs` in the
project root directory. For example to override Buck's default 1GB
heap size:

----
  cat > .buckjavaargs <<EOF
  -XX:MaxPermSize=512m -Xms8000m -Xmx16000m
  EOF
----
