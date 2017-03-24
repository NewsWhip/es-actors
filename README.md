# es-actors

## es-server
```
sbt "project server" run
```

## es-client

```
Usage: es-client [options]

  -i, --indices <index1>,<index2>...
                           
  -d, --dateRange:<start_date>=<end_date>
                           Start date value should be lower than end date.
  -s, --sources <source_address1>,<source_address2>
                           default value 'localhost'
  -p, --sourcePort <source_port>
                           default value 9300
  -c, --sourceCluster <source_cluster>
                           
  -t, --targets <target_address1>,<target_address2>
                           default value 'localhost'
  -r, --targetPort <target_port>
                           default value 9300
  -u, --targetCluster <target_cluster>
                           
  --remoteAddress <remote_address>
                           
  --remotePort <remote_port>
                           
  --remoteName <remote_name>
                           
  --help                   Prints the usage text.
```

### Example:
```

sbt "project client" "run --help"

sbt "project client" "run \
        --indices=index_name \
        --sourceCluster=cluster_name \
        --targetCluster=cluster_name"

sbt "project client" "run \
        -d:2016-10-1=2016-10-25 \
        -c=cluster_name \
        -u=cluster_name"

```
