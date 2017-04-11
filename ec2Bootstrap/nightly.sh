#!/bin/bash

# 45 0 * * * /opt/elasticsearch-migration/es-actors-VERSION/ec2Bootstrap/nightly.sh /opt/elasticsearch-migration/es-actors-1.3.7/ NewsWhipCluster NewsWhipStagingCluster 10.0.1.10,10.0.3.10,10.0.7.10,10.0.9.10 10.0.1.110,10.0.3.110,10.0.7.110,10.0.9.110 9300 4 >/dev/null 2>&1

# Setup cronjob with this script to run
DATE_START=$(date +%Y-%m-%d)

CLIENT_PROJECT="$1"
SOURCE_CLUSTER="--sourceCluster=$2"
TARGET_CLUSTER="--targetCluster=$3"
SOURCE_IPS="--sources=$4"
TARGET_IPS="--targets=$5"
WEEKS="$6"
WS="--ws=$7"
LOGS="-DLOGS_HOME=$8"

INDEX="--indices=$(date +sg-%Y-%-V)"
NIGHTLY="--nightly date=$DATE_START,weeksBack=$WEEKS"

echo "Executing nightly script with date: $DATE_START and weeks: $WEEKS"

CMD="sbt $LOGS -J-Xmx25G -J-Xms25G \"project client\" \"run $SOURCE_CLUSTER $TARGET_CLUSTER $SOURCE_IPS $TARGET_IPS $NIGHTLY $INDEX $WS\""

echo "$CMD"
cd $CLIENT_PROJECT && eval $CMD
