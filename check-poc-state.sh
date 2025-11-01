#!/bin/bash
cdir="$(pwd)"
for pom in $(find . -name "pom.xml" -type f)
do
    cd "$cdir"
    poc_folder=$(dirname $pom)
    echo "==== $pom"
    cd "$poc_folder"
    pwd
    mvn clean package
done