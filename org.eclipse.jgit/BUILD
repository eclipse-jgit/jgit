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
  resource_strip_prefix = 'org.eclipse.jgit/resources',
  deps = [
    '@javaewah//jar',
    '@jsch//jar',
    '@httpclient//jar',
    '@servlet_api_3_1//jar',
    '@log_api//jar',
  ],
)
