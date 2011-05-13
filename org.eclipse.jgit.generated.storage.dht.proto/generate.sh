#!/bin/sh
#
# Update generated Java code from protocol buffer descriptions.

set -e

for proto in resources/org/eclipse/jgit/storage/dht/*.proto
do
  echo >&2 Generating from $proto
  protoc -Iresources --java_out=src $proto
done
