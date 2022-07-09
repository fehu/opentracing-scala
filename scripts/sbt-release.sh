#!/bin/env bash
MAIN=$1
shift 1
args=( "$@" )

scala-cli run project/ReleaseDefs.scala scripts/sbt-release.sc --main-class $MAIN -- "${args[@]}"
