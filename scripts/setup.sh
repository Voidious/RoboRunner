#!/bin/bash
if [ ! $# == 2 ]; then
  echo "Usage: ./setup.sh <number of instances> <path to Robocode>";
  echo ""
  echo "Clones the source Robocode directory to <instances> individual"
  echo "subdirectories. A thread will be spawned for each instance."
  echo ""
else
  if [ -e ./roborunner.properties ]; then
    echo "WARNING: This will overwrite whatever you have in roborunner.properties."
    echo "Continue? [Y/n]"
    read -s -n 1 CONTINUE
    if [ "$CONTINUE" = 'n' -o "$CONTINUE" = 'N' ]; then
      echo "Exiting..."
      exit
    fi
  fi
  if [ -e ./robocodes ]; then
    echo "Clearing existing Robocode installs..."
    rm -rf ./robocodes/*
    echo "  Done!"
  else
    echo "Creating robocodes/ dir..."
    mkdir ./robocodes
    echo "  Done!"
  fi
  paths=""
  for (( x = 1; x <= $1; x++));
  do
    if [ $x -gt 1 ]; then
      paths="$paths,"
    fi
    paths="$paths./robocodes/r$x"
    echo "Copying from $2 to ./robocodes/r$x ..." 
    mkdir ./robocodes/r$x
    cp -r $2/* ./robocodes/r$x
    echo "  Done!"
  done
  if [ ! -e ./data ]; then
    echo "Creating data/ dir..."
    mkdir ./data
    echo "  Done!"
  fi
  if [ ! -e ./bots ]; then
    echo "Creating bots/ dir..."
    mkdir ./bots
    echo "  Done!"
  fi
  echo "robocodePaths=$paths" > roborunner.properties
  echo "jvmArgs=-Xmx1024M -Dapple.awt.UIElement\=true -Djava.security.manager=allow -XX:+IgnoreUnrecognizedVMOptions --add-opens\=java.base/sun.net.www.protocol.jar\=ALL-UNNAMED --add-opens\=java.base/java.lang.reflect\=ALL-UNNAMED --add-opens\=java.desktop/javax.swing.text\=ALL-UNNAMED --add-opens\=java.desktop/sun.awt\=ALL-UNNAMED --add-opens\=java.desktop/java.awt\=ALL-UNNAMED --add-opens\=java.base/java.lang\=ALL-UNNAMED --add-opens\=java.desktop/sun.java2d.opengl\=ALL-UNNAMED" >> roborunner.properties
  echo "botsDirs=./bots" >> roborunner.properties
fi
