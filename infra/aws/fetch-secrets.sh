#!/usr/bin/env bash
# Builds /opt/socialhub/.env from SSM Parameter Store (SecureString, KMS-encrypted).
# Secrets never live in Git or in the Jenkins workspace; the EC2 instance role grants read access
# to /socialhub/prod/* only (see ec2-role-policy.json).
set -euo pipefail
REGION="${AWS_REGION:-ap-south-1}"
PREFIX="/socialhub/prod/"
OUT="/opt/socialhub/.env"

umask 077   # .env readable by the owner only
aws ssm get-parameters-by-path --region "$REGION" --path "$PREFIX" --with-decryption --recursive \
  --query 'Parameters[].[Name,Value]' --output text \
  | while IFS=$'\t' read -r name value; do echo "${name#"$PREFIX"}=${value}"; done > "$OUT"

echo "Wrote $(wc -l < "$OUT") variables to $OUT"
