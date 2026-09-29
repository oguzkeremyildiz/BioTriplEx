#!/bin/sh
# Runs a BioTriplEx command, compiling and resolving the classpath on first use.
#
#   ./run.sh                                    the desktop application
#   ./run.sh evaluate                           score extraction against the gold tags
#   ./run.sh graph --method bioner              build and export the knowledge graph
#   ./run.sh help                               every command and its options
set -e
cd "$(dirname "$0")"
CLASSPATH_FILE=target/classpath.txt
if [ ! -f "$CLASSPATH_FILE" ] || [ pom.xml -nt "$CLASSPATH_FILE" ]; then
    mvn -q compile dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE"
else
    mvn -q compile
fi
exec java -Dorg.slf4j.simpleLogger.defaultLogLevel=warn -cp "target/classes:$(cat "$CLASSPATH_FILE")" org.example.Main "$@"
