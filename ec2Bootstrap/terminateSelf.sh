#/bin/bash

# Script to terminate instance, spot instance will automatically be recreated later

INSTANCE_ID=$(/usr/bin/wget -qO- http://instance-data/latest/meta-data/instance-id)

echo "Teminating $INSTANCE_ID"

/usr/bin/aws ec2 terminate-instances --instance-ids $INSTANCE_ID --region us-east-1
