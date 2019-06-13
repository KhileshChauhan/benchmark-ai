#!/usr/bin/env python3
import os
from dataclasses import dataclass

from typing import Dict, Optional

import subprocess
import argparse
import boto3


def open_terraform_vars_file(filename) -> Dict[str, str]:
    if not os.path.exists(filename):
        return {}

    d = {}
    with open(filename, "r") as f:
        for line in f:
            line = line.strip()
            equal_index = line.index("=")
            key = line[:equal_index]
            value = line[equal_index + 1 :]
            d[key.strip()] = value.strip().strip('"')
    return d


@dataclass
class Config:
    region: str
    github_branch: str
    github_organization: str
    bucket_prefix: Optional[str]
    bucket: Optional[str]

    def __init__(self, args):
        existing_values = {}
        existing_values.update(open_terraform_vars_file(".terraform/ci-backend-config"))
        existing_values.update(open_terraform_vars_file("terraform.tfvars"))

        self.region = args.region or existing_values.get("region") or "us-east-1"
        self.github_branch = args.github_branch or existing_values.get("github_branch") or "master"
        self.github_organization = args.github_organization or existing_values.get("github_organization") or "MXNetEdge"
        self.bucket_prefix = args.bucket_prefix or "bai-ci-terraform-remote-state"
        self.bucket = args.bucket_name or existing_values.get("bucket")

    def write(self):
        os.makedirs(".terraform", exist_ok=True)
        with open(".terraform/ci-backend-config", "w") as f:
            f.write(f'bucket="{self.bucket}"\n')
            f.write(f'region="{self.region}"\n')

        with open("terraform.tfvars", "w") as f:
            f.write(f'region="{self.region}"\n')
            f.write(f'github_organization="{self.github_organization}"\n')
            f.write(f'github_branch="{self.github_branch}"\n')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--region")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--bucket-prefix")
    group.add_argument("--bucket-name")
    parser.add_argument("--github-branch")
    parser.add_argument("--github-organization")
    config = Config(parser.parse_args())

    region = config.region

    session = boto3.Session(region_name=region)

    # Ensure bucket exists for remote state
    if config.bucket is None:
        sts = session.client("sts")
        config.bucket = config.bucket_prefix + "-" + sts.get_caller_identity()["Account"]

    print(f"Will create a bucket named `{config.bucket}` in region `{region}`")

    s3 = session.resource("s3")
    bucket = s3.Bucket(config.bucket)
    if region == "us-east-1":
        # https://github.com/boto/boto3/issues/125#issuecomment-109408790
        bucket.create()
    else:
        bucket.create(CreateBucketConfiguration={"LocationConstraint": region})

    config.write()

    print("=> Configuration to call terraform with:")
    print(config)

    print("=> Calling `terraform init`")
    ret = subprocess.call(["terraform", "init", "-backend-config=.terraform/ci-backend-config"])
    return ret


if __name__ == "__main__":
    main()
