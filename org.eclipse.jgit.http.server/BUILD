package(default_visibility = ['//visibility:public'])

java_library(
  name = 'jgit-servlet',
  srcs = glob(['src/**']),
  resources = glob(['resources/**']),
  deps = [ # We want these deps to be provided_deps
    '//org.eclipse.jgit:jgit',
    '@servlet_api//jar',
  ],
)
