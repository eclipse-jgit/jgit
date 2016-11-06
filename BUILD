package(default_visibility = ['//visibility:public'])

genrule(
  name = 'all',
  srcs = [
    '//org.eclipse.jgit:jgit',
    '//org.eclipse.jgit.archive:jgit-archive',
    '//org.eclipse.jgit.http.server:jgit-servlet',
    '//org.eclipse.jgit.junit:junit',
  ],
  cmd = ' && '.join([
    'p=$$PWD',
    't=$$(mktemp -d || mktemp -d -t bazel-tmp)',
    'cp $(SRCS) $$t',
    'cd $$t',
    'zip -qr $$p/$@ .',
  ]),
  outs = ['all.zip'],
)
