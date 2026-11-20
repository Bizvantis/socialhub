#!/usr/bin/env bash
# EC2 bootstrap (paste as "User data" when launching Ubuntu 24.04).
# Installs Docker + Compose, certbot and the AWS CLI, creates /opt/socialhub, enables swap.
set -euxo pipefail

apt-get update
apt-get install -y ca-certificates curl gnupg rsync jq certbot unzip

# Docker Engine + Compose plugin from Docker's official repository.
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
usermod -aG docker ubuntu
systemctl enable --now docker

# AWS CLI v2 (reads SSM parameters with the instance role; no keys on the machine).
curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-$(uname -m).zip" -o /tmp/awscliv2.zip
unzip -q /tmp/awscliv2.zip -d /tmp && /tmp/aws/install

# Elasticsearch requirement.
echo 'vm.max_map_count=262144' > /etc/sysctl.d/99-elasticsearch.conf
sysctl --system

# 4 GB swap as a safety net for a single-node demo host.
fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

mkdir -p /opt/socialhub /var/www/certbot
chown -R ubuntu:ubuntu /opt/socialhub
