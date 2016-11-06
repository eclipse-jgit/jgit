package(default_visibility = ['//visibility:public'])

# TODO(davido): WalkEncryption.java has security vulnerabilities, that were
# flagged by error prone checker: exclude from build for now:
# http://errorprone.info/bugpattern/InsecureCryptoUsage
# http://paste.openstack.org/show/588104/
SRCS = glob(['src/**'], exclude=['**/WalkEncryption.java', '**/*AmazonS3.java'])
RESOURCES = glob(['resources/**'])

java_library(
  name = 'jgit',
  srcs = SRCS,
  resources = RESOURCES,
  deps = [
    '@javaewah//jar',
    '@jsch//jar',
    '@httpcomponents//jar',
    '@servlet_api//jar',
    '@slf4j_api//jar',
  ],
)
