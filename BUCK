DEPS = [
  '//org.eclipse.jgit:jgit',
  '//org.eclipse.jgit.archive:jgit-archive',
  '//org.eclipse.jgit.http.server:jgit-servlet',
  '//org.eclipse.jgit.lfs:jgit-lfs',
  '//org.eclipse.jgit.lfs.server:jgit-lfs-server',
  '//org.eclipse.jgit.pgm:jgit',
]

zip_file(
  name = 'all',
  srcs = DEPS,
)

sh_binary(
  name = 'jgit_bin',
  main = '//org.eclipse.jgit.pgm:jgit',
)
