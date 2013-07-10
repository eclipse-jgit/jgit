#!/usr/bin/env ruby
require 'rubygems'
require 'highline/import'

def deploy_jar(artifact, version, prefix)
  pom = "#{artifact}-#{version}.pom"
  binary = "#{artifact}-#{version}.jar"
  javadoc = "#{artifact}-#{version}-javadoc.jar"
  sources = "#{artifact}-#{version}-sources.jar"

  args = prefix | ["-DpomFile=#{pom}", "-Dfile=#{binary}"]
  system *args

  args = prefix | ["-DpomFile=#{pom}", "-Dfile=#{sources}",
                   "-Dclassifier=sources"]
  system *args

  args = prefix | ["-DpomFile=#{pom}", "-Dfile=#{javadoc}",
                   "-Dclassifier=javadoc"]
  system *args
end

def deploy_parent(version, prefix)
  pom = "org.eclipse.jgit-parent-#{version}.pom"
  args = prefix | ["-DpomFile=#{pom}", "-Dfile=#{pom}"]
  system *args
end

def deploy_sh(artifact, version, prefix)
  pom = "#{artifact}-#{version}.pom"
  sh = "#{artifact}-#{version}.sh"
  args = prefix | ["-DpomFile=#{pom}", "-Dfile=#{sh}", "-Dpackaging=sh"]
  system *args
end

def get_passphrase(prompt="Enter your GPG Passphrase")
   ask(prompt) {|q| q.echo = false}
end

version = '3.0.0.201306101825-r'.freeze
url = 'https://oss.sonatype.org/service/local/staging/deploy/maven2/'
repositoryId = 'sonatype-nexus-staging'
puts "gpg passphrase ?"
passphrase = get_passphrase()

artifacts = ['org.eclipse.jgit',
             'org.eclipse.jgit.ant',
             'org.eclipse.jgit.console',
             'org.eclipse.jgit.http.server',
             'org.eclipse.jgit.java7',
             'org.eclipse.jgit.junit',
             'org.eclipse.jgit.junit.http',
             'org.eclipse.jgit.pgm',
             'org.eclipse.jgit.ui']

prefix =  "mvn", "gpg:sign-and-deploy-file", "-Dgpg.passphrase=#{passphrase}",
          "-Durl=#{url}", "-DrepositoryId=#{repositoryId}"
deploy_parent(version, prefix)
artifacts.each do |artifact|
  deploy_jar(artifact, version, prefix)
end
deploy_sh('org.eclipse.jgit.pgm', version, prefix)
