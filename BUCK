include_defs('//tools/pgm.defs')

shjava_binary(name='jgit',  jar='//org.eclipse.jgit.pgm:jgit_main')
shjava_binary(name='jgit6', jar='//org.eclipse.jgit.pgm:jgit6_main')

BUNDLE_DEPS = [
  '//org.eclipse.jgit:bundle',
  '//org.eclipse.jgit.ant:bundle',
  '//org.eclipse.jgit.archive:bundle',
  '//org.eclipse.jgit.http.server:bundle',
  '//org.eclipse.jgit.java7:bundle',
  '//org.eclipse.jgit.junit:bundle',
]

genrule(
  name = 'bundles',
  cmd = 'cd $TMP;touch bundles.done',
  deps = BUNDLE_DEPS,
  out = 'bundles.done',
)
