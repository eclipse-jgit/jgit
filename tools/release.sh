#!/bin/bash
#
# script to create a jgit release

# uncomment to switch on trace
#set -x

# abort if a command hits an error
set -e

export basePath=$(cd "$(dirname "$0")"; pwd)
echo basePath $basePath

if [ -z $1 ]; then
    echo "
    Usage:
    $ release.sh <release version tag>

    e.g. release.sh v3.4.0.201405051725-m7
"
    exit
fi

# trimmed git status
export status=$(git status --porcelain)

if [ ! -z "$status" ];
then
    echo "
    working tree is dirty -> can't create release
"
    exit
fi

MSG="JGit $1"

# tag release
git tag -s -m "$MSG" $1

# update version numbers
./tools/version.sh --release

# commit changed version numbers
git commit -a -s -m "$MSG"

# move the tag to the version we release
git tag -sf -m "$MSG" $1

# run the build
mvn clean install
mvn clean install -f org.eclipse.jgit.packaging/pom.xml

