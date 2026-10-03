#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
kubectl delete -k k8s/ --ignore-not-found
kubectl -n telecom-orders delete pvc --all --ignore-not-found 2>/dev/null || true
kubectl delete namespace telecom-orders --ignore-not-found
