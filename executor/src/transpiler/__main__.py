#  Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
#
#  Licensed under the Apache License, Version 2.0 (the "License").
#  You may not use this file except in compliance with the License.
#  A copy of the License is located at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  or in the "license" file accompanying this file. This file is distributed
#  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
#  express or implied. See the License for the specific language governing
#  permissions and limitations under the License.
import os
import toml

from argparse import ArgumentParser
from executor.args import create_executor_config
from transpiler.bai_knowledge import create_job_yaml_spec
from uuid import uuid4


def main(argv=None):
    # This method is only called when using the transpiler as a module of its own, which
    # is getting deprecated very soon
    transpiler_config = create_executor_config(argv)
    input = get_input_args(argv)
    descriptor_data = toml.load(input.descriptor)

    # TODO: Pass this as an argument
    fetched_data_sources = descriptor_data.get("data", {}).get("sources", [])

    yaml_string = create_job_yaml_spec(descriptor_data, transpiler_config, fetched_data_sources, str(uuid4()))

    if input.filename:
        current_dir = os.path.dirname(os.path.abspath(__file__))
        with open(os.path.join(current_dir, input.filename), "w") as f:
            f.write(yaml_string)
    else:
        print(yaml_string)


def get_input_args(argv):
    parser = ArgumentParser()

    parser.add_argument("--descriptor", help="Relative path to descriptor file", required=True)

    parser.add_argument("-f", "--filename", help="Output to file. If not specified, output to stdout", default=None)

    parsed_args, _ = parser.parse_known_args(argv)
    return parsed_args


if __name__ == "__main__":
    main()
