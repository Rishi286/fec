#!/bin/bash
set -e

TFVARS="$1"
if [ -z "$TFVARS" ]; then
  echo "usage: ./build.sh deployments/<name>.tfvars" >&2
  exit 1
fi

tfvar() {
  grep -E "^$1[[:space:]]*=" "$TFVARS" | sed -E 's/^[a-z_]+[[:space:]]*=[[:space:]]*"(.*)"[[:space:]]*$/\1/'
}

PREFIX=$(tfvar prefix)
PROJECT_ROOT=$(tfvar project_root)
PROCESSOR_BUILD=$(tfvar processor_build_command)
DASHBOARD_BUILD=$(tfvar dashboard_build_command)

echo "== building $PREFIX in $PROJECT_ROOT =="

cd "$(dirname "$0")/$PROJECT_ROOT"
PROJECT_ROOT_ABS="$(pwd)"

echo "-- processor: $PROCESSOR_BUILD"
(eval "$PROCESSOR_BUILD")

echo "-- dashboard: $DASHBOARD_BUILD"
(eval "$DASHBOARD_BUILD")

echo "-- tarring sensors/fog/infra"
BUILD_DIR="/tmp/${PREFIX}-build"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
tar -czf "$BUILD_DIR/deploy-src.tar.gz" -C "$PROJECT_ROOT_ABS" sensors fog infra

echo "== build complete, ready for: terraform apply -var-file=$TFVARS =="
