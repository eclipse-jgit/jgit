java_library(
  name = 'jgit',
  exported_deps = ['//org.eclipse.jgit:jgit'],
  visibility = ['PUBLIC'],
)

java_library(
  name = 'jgit_src',
  exported_deps = ['//org.eclipse.jgit:jgit_src'],
  visibility = ['PUBLIC'],
)

java_library(
  name = 'jgit-servlet',
  exported_deps = [
    ':jgit',
    '//org.eclipse.jgit.http.server:jgit-servlet'
  ],
  visibility = ['PUBLIC'],
)

java_library(
  name = 'jgit-archive',
  exported_deps = [
    ':jgit',
    '//org.eclipse.jgit.archive:jgit-archive'
  ],
  visibility = ['PUBLIC'],
)

java_library(
  name = 'junit',
  exported_deps = [
    ':jgit',
    '//org.eclipse.jgit.junit:junit'
  ],
  visibility = ['PUBLIC'],
)
