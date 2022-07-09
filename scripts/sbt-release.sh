#!/bin/sh
MAIN=$1
shift 1

scala-cli run project/ReleaseDefs.scala scripts/sbt-release.sc --main-class $MAIN -- $@
