package(default_visibility = ['//visibility:public'])

java_library(
  name = 'junit',
  srcs = glob(['src/**']),
  resources = glob(['resources/**']),
  resource_strip_prefix = 'org.eclipse.jgit.junit/resources',
  deps = [ # We want these deps to be provided_deps
    '//org.eclipse.jgit:jgit',
    '@junit//jar',
  ],
)
