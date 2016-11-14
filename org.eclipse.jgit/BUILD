load("//tools/checker:checker.bzl", "java_library_with_nullness_test")

package(default_visibility = ['//visibility:public'])

INSECURE_CIPHER_FACTORY = [
  'src/org/eclipse/jgit/transport/InsecureCipherFactory.java',
]

SRCS = glob(['src/**'], exclude=INSECURE_CIPHER_FACTORY)
RESOURCES = glob(['resources/**'])

java_library_with_nullness_test(
  name = 'jgit',
  srcs = SRCS,
  resources = RESOURCES,
  resource_strip_prefix = 'org.eclipse.jgit/resources',
  deps = [
    ':insecure_cipher_factory',
    '@javaewah//jar',
    '@jsch//jar',
    '@httpclient//jar',
    '@servlet_api_3_1//jar',
    '@log_api//jar',
  ],
)

java_library_with_nullness_test(
  name = 'insecure_cipher_factory',
  srcs = INSECURE_CIPHER_FACTORY,
  javacopts = ['-Xep:InsecureCipherMode:OFF'],
)
