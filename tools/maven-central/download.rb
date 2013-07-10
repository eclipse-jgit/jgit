#!/usr/bin/env ruby
version = '3.1.0.201310021548-r'.freeze
group = 'org.eclipse.jgit'
artifacts = [group,
             group + '.ant',
             group + '.archive',
             group + '.console',
             group + '.http.server',
             group + '.java7',
             group + '.junit',
             group + '.junit.http',
             group + '.pgm',
             group + '.ui']

puts 'Deleting current files'
`rm -fr *.jar *.sh *.pom`

puts 'Downloading org.eclipse.jgit-parent'
`curl -s https://repo.eclipse.org/content/repositories/jgit-releases/org/eclipse/jgit/#{group}-parent/#{version}/#{group}-parent-#{version}.pom -o #{group}-parent-#{version}.pom`

artifacts.each {|artifact|
  puts "Downloading #{artifact}-#{version}.jar"
  `curl -s https://repo.eclipse.org/content/repositories/jgit-releases/org/eclipse/jgit/#{artifact}/#{version}/#{artifact}-#{version}.jar -o #{artifact}-#{version}.jar`
  `curl -s https://repo.eclipse.org/content/repositories/jgit-releases/org/eclipse/jgit/#{artifact}/#{version}/#{artifact}-#{version}.pom -o #{artifact}-#{version}.pom`
  `curl -s https://repo.eclipse.org/content/repositories/jgit-releases/org/eclipse/jgit/#{artifact}/#{version}/#{artifact}-#{version}-javadoc.jar -o #{artifact}-#{version}-javadoc.jar`
  `curl -s https://repo.eclipse.org/content/repositories/jgit-releases/org/eclipse/jgit/#{artifact}/#{version}/#{artifact}-#{version}-sources.jar -o #{artifact}-#{version}-sources.jar`
}

puts "Downloading org.eclipse.jgit.pgm-#{version}.sh"
`curl -s https://repo.eclipse.org/content/repositories/jgit-releases/org/eclipse/jgit/#{group}.pgm/#{version}/#{group}.pgm-#{version}.sh -o #{group}.pgm-#{version}.sh`
