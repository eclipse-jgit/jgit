package(
  default_visibility = ["//visibility:public"],
)
load(
    "@com_googlesource_gerrit_bazlets//tools:genrule2.bzl",
    "genrule2",
)

java_library(
  name = 'pgm',
  srcs = glob(['src/**']),
  resources = glob(['resources/**']),
  deps = [
    ':services',
    '//org.eclipse.jgit:jgit',
    '//org.eclipse.jgit.archive:jgit-archive',
    '//org.eclipse.jgit.http.apache:http-apache',
    '//org.eclipse.jgit.lfs:jgit-lfs',
    '//org.eclipse.jgit.lfs.server:jgit-lfs-server',
    '//org.eclipse.jgit.ui:ui',
    '@args4j//jar',
    '@httpclient//jar',
    '@httpcore//jar',
    '@jetty_http//jar',
    '@jetty_io//jar',
    '@jetty_server//jar',
    '@jetty_servlet//jar',
    '@jetty_security//jar',
    '@jetty_util//jar',
    '@servlet_api//jar'
  ],
)

java_import(
  name = 'services',
  jars  = [':services__jar',],
)

genrule2(
  name = 'services__jar',
  cmd = 'o=$$PWD/$@ && zip -qr $$o $(SRCS)',
  srcs = glob(['META-INF/services/*']),
  outs = ['services.jar',],
)

genrule2(
  name = 'gen_jgit',
  cmd = ''.join([
    'mkdir $$TMP/META-INF &&',
    'cp $(location :binary_manifest) $$TMP/META-INF/MANIFEST.MF &&',
    'cp $(location :jgit_jar_deploy.jar) $$TMP/jgit.jar &&',
    'cd $$TMP && zip $$TMP/jgit.jar META-INF/MANIFEST.MF &&',
    'cat $(location :jgit.sh) $$TMP/jgit.jar >$@ &&',
    'chmod a+x $@',
  ]),

  srcs = ['jgit.sh',
    ":binary_manifest", ":jgit_jar_deploy.jar",
  ],
  outs = ['jgit'],
)

java_binary(
  name = 'jgit_jar',
  main_class = 'NeverExecuted',
  runtime_deps = [
    ':pgm',
    '@slf4j_simple//jar',
    '@tukaani_xz//jar',
  ],
)

genrule2(
  name = 'binary_manifest',
  cmd = ';'.join(['echo "%s: %s" >>$@' % e for e in [
    ('Manifest-Version', '1.0'),
    ('Main-Class', 'org.eclipse.jgit.pgm.Main'),
    # TODO(hanwen): fix this.
    ('Bundle-Version',"v4.6.1.201703071140-r-133-g50ac85255"),
    ('Implementation-Title', 'JGit Command Line Interface'),
    ('Implementation-Vendor', 'Eclipse.org - JGit'),
    ('Implementation-Vendor-URL', 'http://www.eclipse.org/jgit/'),
    ('Implementation-Vendor-Id', 'org.eclipse.jgit'),
  ]] + ['echo >>$@']),
  outs = ['MANIFEST.MF',],
)
