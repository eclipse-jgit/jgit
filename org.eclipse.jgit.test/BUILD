package(
  default_visibility = ["//visibility:public"],
)

load(
    "@com_googlesource_gerrit_bazlets//tools:genrule2.bzl",
    "genrule2",
)


PKG = 'tst/org/eclipse/jgit/'

HELPERS = glob(['src/**/*.java']) + [PKG + c for c in [
  'api/AbstractRemoteCommandTest.java',
  'diff/AbstractDiffTestCase.java',
  'internal/storage/file/GcTestCase.java',
  'internal/storage/file/PackIndexTestCase.java',
  'internal/storage/file/XInputStream.java',
  'nls/GermanTranslatedBundle.java',
  'nls/MissingPropertyBundle.java',
  'nls/NoPropertiesBundle.java',
  'nls/NonTranslatedBundle.java',
  'revwalk/RevQueueTestCase.java',
  'revwalk/RevWalkTestCase.java',
  'transport/SpiTransport.java',
  'treewalk/FileTreeIteratorWithTimeControl.java',
  'treewalk/filter/AlwaysCloneTreeFilter.java',
  'test/resources/SampleDataRepositoryTestCase.java',
  'util/CPUTimeStopWatch.java',
  'util/io/Strings.java',
]]

DATA = [
  PKG + 'lib/empty.gitindex.dat',
  PKG + 'lib/sorttest.gitindex.dat',
]

TESTS = glob(
  ['tst/**/*.java'],
  exclude = HELPERS + DATA,
)

DEPS = {
  PKG + 'nls/RootLocaleTest.java': [
    '//org.eclipse.jgit.pgm:pgm',
    '//org.eclipse.jgit.ui:ui',
  ],
}

[java_test(
    name = src[len('tst/'):len(src)-len('.java')].replace('/', '_'),
    test_class = src[len('tst/'):len(src)-len('.java')].replace('/', '.'),
    srcs = [src],
    deps = [
      ':helpers',
      ':tst_rsrc',
      '//org.eclipse.jgit:jgit',
      '//org.eclipse.jgit.junit:junit',
      '//org.eclipse.jgit.lfs:jgit-lfs',
      '@hamcrest_core//jar',
      '@hamcrest_library//jar',
      '@javaewah//jar',
      '@junit//jar',
      '@log_api//jar',
      '@slf4j_simple//jar',
    ] + DEPS.get(src, []),
    jvm_flags = ['-Xmx256m', '-Dfile.encoding=UTF-8'],
  )
for src in TESTS]

java_library(
  name = 'helpers',
  srcs = HELPERS,
  resources = DATA,
  deps = [
    '//org.eclipse.jgit:jgit',
    '//org.eclipse.jgit.junit:junit',
    '@junit//jar',
  ],
)

java_import(
  name = 'tst_rsrc',
  jars = [':tst_rsrc_jar',],
)

genrule2(
  name = 'tst_rsrc_jar',
  cmd = 'o=$$PWD/$@ && tar cf - $(SRCS) | tar -C $$TMP --strip-components=2 -xf - && cd  $$TMP && zip -qr $$o .',
  srcs = glob(['tst-rsrc/**']),
  outs = ['tst_rsrc.jar',],
)
