#!/bin/bash

unameOut="$(uname -s)"
case "${unameOut}" in
	CYGWIN*)    DIR=`cygpath -wa $(dirname "$0")`;;
    *)        DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )";;
esac
JASM_INSTALL_DIR="$DIR/../dist"

if [ -z "$JAVA_HOME" ]; then
	echo "Please set the variable JASM_JAVA_HOME to the java runtime's directory. You need at least JRE 1.8"
else 
	JASM_JAVA_CMD="$JAVA_HOME"/bin/java
	if [ -f "$JASM_JAVA_CMD" ]; then 
#		JASM_INSTALL_DIR=`dirname "$0"`
		"$JASM_JAVA_CMD" -classpath "$JASM_INSTALL_DIR/*" org.jasm.tools.Assembler $*
	else 
		echo "Couldn't find $JASM_JAVA_CMD! Please adjust JASM_JAVA_HOME to the correct value."
	fi
fi


