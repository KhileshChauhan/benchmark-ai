<!---
  Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.

  Licensed under the Apache License, Version 2.0 (the "License").
  You may not use this file except in compliance with the License.
  A copy of the License is located at

      http://www.apache.org/licenses/LICENSE-2.0

  or in the "license" file accompanying this file. This file is distributed
  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
  express or implied. See the License for the specific language governing
  permissions and limitations under the License.
-->
# Metrics extractor 

This service runs in a container and listens to stderr/stdout (k8s log) of a container running in the same pod. 
Logs a read, parsed and pushed further to metrics pusher. The patterns and the names of the metric are configured 
in the toml descriptors of anubis jobs.

## Need 

Users of Anubis need insights about jobs they are running on the system. Operational metrics is provided by Prometheus 
exporters on Kubernetes nodes, but ML related jobs have custom metrics such as accuracy, throughput, error rate etc. 
Every job could have a different specific metric, sometimes not applicable to other jobs. The reporting of such metrics 
needs to be real time. 

In some cases, the container specified might not provide needed instrumentation of the running script (benchmark-ai 
client library). For this cases, the metrics need to be extracted in an non-intrusive manner by parsing log output of 
the executed container.


## Approach

Since custom metrics can be specific to each benchmark job, they need to be defined in the toml as well. 

**Example .toml**

```
# 5. Output
[output]
# [Opt] Custom metrics descriptions
# List all required metrics descriptions below.
# Make an entry in same format as the one below.
[[output.metrics]]
# Name of the metric that will appear in the dashboards.
name = "accuracy"
# Pattern for log parsing for this metric.
pattern = "accuracy=[0-9\.]+"
# [Opt] Units for the metric (can be used in the dashboard as well)
units = "%"
# List all required metrics descriptions below.
# Make an entry in same format as the one below.
[[output.metrics]]
# Name of the metric that will appear in the dashboards.
name = "throughput"
# Pattern for log parsing for this metric.
pattern = "throughput=[0-9\.]+"
# [Opt] Units for the metric (can be used in the dashboard as well)
units = "img/sec"
```

Refer to the [single node example descriptor](../sample-benchmarks/single-node/descriptor_cpu.toml) for an instance of a
descriptor which uses this feature.


### Architecture

![metrics-extractor-architecture](../docs/images/metrics-extractor.png 'Metrics extractor architecture')


## Python dependencies

requirements.txt does not contain the required pip dependencies since they are managed through conda and are listed 
in environment.yml.
