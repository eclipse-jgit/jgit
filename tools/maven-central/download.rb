#!/usr/bin/env ruby
version = '3.0.0.201306101825-r'.freeze
group = 'org.eclipse.jgit'
artifacts = [group,
             group + '.ant',
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
`curl -s http://download.eclipse.org/jgit/maven/org/eclipse/jgit/#{group}-parent/#{version}/#{group}-parent-#{version}.pom -o #{group}-parent-#{version}.pom`

artifacts.each {|artifact|
  puts "Downloading #{artifact}-#{version}.jar"
  `curl -s http://download.eclipse.org/jgit/maven/org/eclipse/jgit/#{artifact}/#{version}/#{artifact}-#{version}.jar -o #{artifact}-#{version}.jar`
  `curl -s http://download.eclipse.org/jgit/maven/org/eclipse/jgit/#{artifact}/#{version}/#{artifact}-#{version}.pom -o #{artifact}-#{version}.pom`
  `curl -s http://download.eclipse.org/jgit/maven/org/eclipse/jgit/#{artifact}/#{version}/#{artifact}-#{version}-javadoc.jar -o #{artifact}-#{version}-javadoc.jar`
  `curl -s http://download.eclipse.org/jgit/maven/org/eclipse/jgit/#{artifact}/#{version}/#{artifact}-#{version}-sources.jar -o #{artifact}-#{version}-sources.jar`
}

puts "Downloading org.eclipse.jgit.pgm-#{version}.sh"
`curl -s http://download.eclipse.org/jgit/maven/org/eclipse/jgit/#{group}.pgm/#{version}/#{group}.pgm-#{version}.sh -o #{group}.pgm-#{version}.sh`
