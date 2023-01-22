#!/usr/bin/env bash
echo "Clearing Robocode JARs, robot caches and databases... "
cd robocodes
rm -rf r*/robots/.data
rm r*/robots/robot.database
#rm r*/robots/*.jar
find r*/robots -name '*.jar' -delete
echo "  Done!"

