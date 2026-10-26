#!/usr/bin/env bash
# SmartHire production VM bootstrap.
#
# Target: Ubuntu 22.04/24.04 on Oracle Cloud Infrastructure "Always Free" Ampere A1 (aarch64),
# or any comparable single Linux VM. Installs Docker Engine + Compose v2, adds a swap file as an
# OOM cushion, opens SSH/HTTP/HTTPS in the host firewall, and creates the directories used by the
# production stack and its backup timer.
#
# Usage (on the VM, from a checkout of this repository):
#   sudo ./deploy/scripts/bootstrap-vm.sh
#
# Idempotent: safe to re-run. It does NOT change the cloud provider firewall (for OCI that is the
# VCN security list), which must also allow ingress TCP 22/80/443. See
# docs/deployment/production-runbook.md.
set -euo pipefail

log() { printf '\n[bootstrap] %s\n' "$*"; }

if [[ ${EUID} -ne 0 ]]; then
  echo "Run with sudo: sudo $0" >&2
  exit 1
fi

TARGET_USER="${SUDO_USER:-root}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# ---- Packages -------------------------------------------------------------------------------
export DEBIAN_FRONTEND=noninteractive
# Answer the iptables-persistent install prompts non-interactively.
echo "iptables-persistent iptables-persistent/autosave_v4 boolean true" | debconf-set-selections
echo "iptables-persistent iptables-persistent/autosave_v6 boolean true" | debconf-set-selections

log "Installing base packages"
apt-get update -y
apt-get install -y --no-install-recommends ca-certificates curl gnupg iptables-persistent

log "Installing Docker Engine from the official repository"
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

# shellcheck disable=SC1091
. /etc/os-release
ARCH="$(dpkg --print-architecture)"
printf 'deb [arch=%s signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu %s stable\n' \
  "$ARCH" "${VERSION_CODENAME}" > /etc/apt/sources.list.d/docker.list

apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

systemctl enable --now docker
usermod -aG docker "$TARGET_USER"

# ---- Swap -----------------------------------------------------------------------------------
# The JVM services are memory-limited by compose, but a small swap file keeps the kernel from
# killing a service during a cold image build on a 12 GiB VM.
if swapon --show | grep -q .; then
  log "Swap already enabled, leaving it untouched"
else
  log "Creating a 2 GiB swap file"
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || printf '/swapfile none swap sw 0 0\n' >> /etc/fstab
  printf 'vm.swappiness=10\n' > /etc/sysctl.d/99-smarthire-swap.conf
  sysctl -w vm.swappiness=10 >/dev/null
fi

# ---- Host firewall --------------------------------------------------------------------------
# OCI's Ubuntu images ship an iptables INPUT chain ending in REJECT. Prepend explicit accepts so
# the rules survive both reboots (netfilter-persistent) and Docker's own chain management.
log "Allowing TCP 22/80/443 in the host firewall"
for port in 22 80 443; do
  iptables -C INPUT -p tcp --dport "$port" -j ACCEPT 2>/dev/null \
    || iptables -I INPUT 1 -p tcp --dport "$port" -j ACCEPT
done
netfilter-persistent save

# ---- Directories ----------------------------------------------------------------------------
# /opt/smarthire is the checkout path expected by deploy/systemd/*.service.
log "Creating SmartHire directories"
install -d -o "$TARGET_USER" -g "$TARGET_USER" /opt/smarthire
install -d -o "$TARGET_USER" -g "$TARGET_USER" /var/backups/smarthire
install -d -o root -g root /etc/smarthire

# ---- Report ---------------------------------------------------------------------------------
log "Versions"
docker --version
docker compose version
log "Bootstrap complete."
log "Repository checkout expected at: /opt/smarthire (currently: $REPO_ROOT)"
log "Log out and back in (or run 'newgrp docker') before running docker without sudo."
