java_library(
  name = 'jgit',
  exported_deps = ['//org.eclipse.jgit:jgit'],
  visibility = ['PUBLIC'],
)

genrule(
  name = 'jgit_src',
  cmd = 'ln -s $(location //org.eclipse.jgit:jgit_src) $OUT',
  out = 'jgit_src.zip',
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

genrule(
  name = 'jgit_bin',
  cmd = 'ln -s $(location //org.eclipse.jgit.pgm:jgit) $OUT',
  out = 'jgit_bin',
)
