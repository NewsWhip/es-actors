#!/usr/bin/env bash

ES_ACTORS_VERSION=2.0.1-alpha

WORKDIR=/opt/elasticsearch-migration

cd $WORKDIR && rm -rf v$ES_ACTORS_VERSION.tar.gz es-actors-$ES_ACTORS_VERSION

# Setup server
wget https://github.com/NewsWhip/es-actors/archive/v$ES_ACTORS_VERSION.tar.gz
tar -xvf v$ES_ACTORS_VERSION.tar.gz
cd es-actors-$ES_ACTORS_VERSION/es-actors
chmod 755 $WORKDIR/es-actors-$ES_ACTORS_VERSION/ec2Bootstrap/nightly.sh

SERVER_CMD="sbt -DLOGS_HOME=$WORKDIR/logs -J-Xmx25G -J-Xms25G \"project server\" run"
echo $SERVER_CMD

ORIGIN_CLUSTER="NewsWhipStagingCluster"
ORIGIN_NODES="10.0.1.110,10.0.3.110,10.0.7.110,10.0.9.110,10.0.1.111"
TARGET_CLUSTER="NewsWhipTestCluster"
TARGET_NODES="10.0.9.104,10.0.7.166,10.0.9.55,10.0.7.189,10.0.1.62"
WS="http://10.0.1.20:8000/article"
LOGS="$WORKDIR/logs"

echo "$WORKDIR/es-actors-$ES_ACTORS_VERSION/ec2Bootstrap/nightly.sh $WORKDIR/es-actors-$ES_ACTORS_VERSION/es-actors $ORIGIN_CLUSTER $TARGET_CLUSTER $ORIGIN_NODES $TARGET_NODES 4 $WS $LOGS"