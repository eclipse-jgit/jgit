JGitNIO
=====

JGitNIO is a jgit based file system for Java 8 and above, implementing the
[java.nio.file](http://docs.oracle.com/javase/8/docs/api/java/nio/file/package-summary.html)
abstract file system APIs.

Getting started
---------------

```xml
<dependency>
  <groupId>org.eclipse.jgit</groupId>
  <artifactId>org.eclipse.jgit.niofs</artifactId>
  <version>5.10.0-SNAPSHOT</version>
</dependency>
```

Basic use
---------

The simplest way to use JGit NIO is to just create a new `FileSystem` instance using:

```java
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

...

final FileSystem value = FileSystems.newFileSystem(URI.create("git://myrepo" + new Random().nextInt()), new HashMap<>());

final Path path = Files.write(value.getPath("filename.txt"), "content".getBytes());

Files.readAllLines(path).get(0);
```
