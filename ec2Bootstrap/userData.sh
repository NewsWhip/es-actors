#!/bin/bash -ex

# Debian apt-get install function
apt_get_install()
{
        DEBIAN_FRONTEND=noninteractive apt-get -y \
        -o DPkg::Options::=--force-confdef \
        -o DPkg::Options::=--force-confold \
        install $@
}

# Mark execution start
echo "STARTING" > /root/user_data_run

# Some initial setup
set -e -x
export DEBIAN_FRONTEND=noninteractive
apt-get update && apt-get upgrade -y

# Install required packages
apt_get_install git
add-apt-repository ppa:webupd8team/java
apt-get update
apt_get_install oracle-java8-installer

mkdir /opt/elasticsearch-migration
cd /opt/elasticsearch-migration
wget https://github.com/NewsWhip/es-actors/archive/v1.3.7.tar.gz
tar -xvf v1.3.7.tar.gz
cd es-actors-1.3.7
SERVER_CMD="sbt -J-Xmx10G -J-Xms10G \"; project server; run-main com.broilogabriel.Server\""
eval $SERVER_CMD </dev/null &>/dev/null &

# Mark execution end
echo "DONE" > /root/user_data_run

