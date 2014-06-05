#!/usr/bin/env ruby
require 'rubygems'
require 'highline/import'

def run(args)
  system(*args)
end

def deploy_jar(artifact, version, prefix)
  pom = "#{artifact}-#{version}.pom"
  binary = "#{artifact}-#{version}.jar"
  javadoc = "#{artifact}-#{version}-javadoc.jar"
  sources = "#{artifact}-#{version}-sources.jar"

  run prefix + ["-DpomFile=#{pom}", "-Dfile=#{binary}"]
  run prefix + ["-DpomFile=#{pom}", "-Dfile=#{sources}",
                   "-Dclassifier=sources"]
  run prefix + ["-DpomFile=#{pom}", "-Dfile=#{javadoc}",
                   "-Dclassifier=javadoc"]
end

def deploy_parent(version, prefix)
  pom = "org.eclipse.jgit-parent-#{version}.pom"
  run prefix + ["-DpomFile=#{pom}", "-Dfile=#{pom}"]
end

def deploy_sh(artifact, version, prefix)
  pom = "#{artifact}-#{version}.pom"
  sh = "#{artifact}-#{version}.sh"
  run prefix + ["-DpomFile=#{pom}", "-Dfile=#{sh}", "-Dpackaging=sh"]
end

def get_passphrase(prompt="Enter your GPG Passphrase")
   ask(prompt) {|q| q.echo = false}
end

version = ARGV[0].freeze
if version =~ /\A(\d+\.\d+\.\d+)\.(\d{12})-(m\d|rc\d|r)\Z/
   printf "version %s qualifier %s classifier %s\n", $1, $2, $3
else
   printf "invalid version %s\n", version
   abort
end

url = 'https://oss.sonatype.org/service/local/staging/deploy/maven2/'
repositoryId = 'sonatype-nexus-staging'
puts "gpg passphrase ?"
passphrase = get_passphrase()

group = 'org.eclipse.jgit'
artifacts = [group,
             group + '.ant',
             group + '.archive',
             group + '.console',
			 group + '.http.apache',
             group + '.http.server',
             group + '.java7',
             group + '.junit',
             group + '.junit.http',
             group + '.pgm',
             group + '.ui']

prefix = ["mvn", "gpg:sign-and-deploy-file", "-Dgpg.passphrase=#{passphrase}",
          "-Durl=#{url}", "-DrepositoryId=#{repositoryId}"]
deploy_parent(version, prefix)
artifacts.each do |artifact|
  deploy_jar(artifact, version, prefix)
end
deploy_sh('org.eclipse.jgit.pgm', version, prefix)
