#!/bin/bash

# 45 0 * * * /opt/elasticsearch-migration/es-actors-1.3.7/ec2Bootstrap/nightly.sh /opt/elasticsearch-migration/es-actors-1.3.7/ NewsWhipCluster NewsWhipStagingCluster 10.0.1.10,10.0.3.10,10.0.7.10,10.0.9.10 10.0.1.110,10.0.3.110,10.0.7.110,10.0.9.110 9300 8 >/dev/null 2>&1

# Setup cronjob with this script to run
DATE_START=$(date +%Y-%m-%d)

CLIENT_PROJECT="$1"
SOURCE_CLUSTER="$2"
TARGET_CLUSTER="$3"
SOURCE_IPS="$4"
TARGET_IPS="$5"
TARGET_PORT="$6"
WEEKS="$7"
NIGHTLY="--nightly date=$DATE_START,weeksBack=$WEEKS"

echo "Executing nightly script with date: $DATE_START and weeks: $WEEKS"

CMD="sbt -J-Xmx25G -J-Xms25G \"; project client; run-main com.broilogabriel.Client --sourceCluster=$SOURCE_CLUSTER --targetCluster=$TARGET_CLUSTER --sources=$SOURCE_IPS --targets=$TARGET_IPS $NIGHTLY\""

echo "$CMD"
cd $CLIENT_PROJECT && eval $CMD
