JGitNIO
=====

JGitNIO is a jgit based file system for Java 8 and above, implementing the
[java.nio.file](http://docs.oracle.com/javase/8/docs/api/java/nio/file/package-summary.html)
abstract file system APIs.

Getting started
---------------

```xml
<dependency>
  <groupId>me.porcelli</groupId>
  <artifactId>jgit-nio2</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Basic use
---------

The simplest way to use JGitNIO is to just create a new `FileSystem` instance using:

```java
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import me.porcelli.nio.jgit.JGitFileSystemBuilder;
...

final FileSystem fs = JGitFileSystemBuilder.newFileSystem("reponame");

Path foo = fs.getPath("/foo");
Files.createDirectory(foo);

Path hello = foo.resolve("hello.txt"); // /foo/hello.txt

Files.write(hello, Collections.singletonList("hello world"), StandardCharsets.UTF_8);

Files.readAllLines(hello).get(0);
```
