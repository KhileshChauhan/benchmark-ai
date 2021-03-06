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
# baictl

TODO


# baictl-infrastructure
Baictl-infrastructure allows you to run create/destroy infra remotely. This makes sure that you will not end up in a corrupted state in case you experience a connectionloss or similar interruptions that may happen during this lengthy process.

## Setup

Before proceeding, please create an AWS Profile with Administrator permissions for your AWS CLI as described here: https://docs.aws.amazon.com/en_us/cli/latest/userguide/cli-configure-profiles.html

This script uses Python and has a few dependencies. Please make sure Python 3 is installed before running ```pip3 install -r requirements.baictl-infrastructure.txt``` to install them.

Fill in the ```config.yml``` file with the values that match your environment.

## Usage
In both cases, you will be prompted for your previously created AWS Profile. You can avoid this prompt by setting the AWS_PROFILE environment variable as follows:
```
export AWS_PROFILE=profile-name
```

To create the Benchmark AI infrastructure, run the following command:
```
./baictl-infrastructure.py create
```


To destroy the Benchmark AI infrastructure, run the following command:
```
./baictl-infrastructure.py destroy
```
