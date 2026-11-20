# Runbook: public demo on a single VM (Azure for Students / Oracle Cloud)

One Ubuntu VM runs the whole stack (`docker-compose.yml` + `docker-compose.full.yml` +
`docker-compose.vm.yml`) with media in SeaweedFS on the same machine. Minimum: 2 vCPU, 8 GB RAM,
64 GB disk. For the AWS variant (S3, registry images, Jenkins deploys) see [aws.md](aws.md).

## 1. Create the VM

**Azure for Students** (no credit card; $100 credit): Virtual machines -> Create
- Image **Ubuntu Server 24.04 LTS x64 Gen2**, size **B2as_v2** (2 vCPU, 8 GiB), ~US$60/month
- SSH public key, username `azureuser`; inbound ports **22, 80, 443**; OS disk 64 GiB Standard SSD
- After creation: Overview -> DNS name -> set a label, e.g. `socialhub-demo`
  -> site = `socialhub-demo.<region>.cloudapp.azure.com`
- Stop (deallocate) the VM when not needed to save credit; the IP and DNS name are kept.

**Oracle Cloud Always Free** (needs a card for verification): shape VM.Standard.A1.Flex
(4 OCPU / 24 GB, arm64), Ubuntu 24.04, public IPv4, ingress TCP 80/443 in the security list.
Site = `<ip-with-dashes>.sslip.io`.

Media hostname (both): `media.<ip-with-dashes>.sslip.io` (sslip.io resolves it to the IP).

## 2. Ship the code and demo media
Jars are built on the VM (in a Maven container), so only sources and media are uploaded.
```bash
python tools/seed/fetch_assets.py && python tools/seed/make_media.py   # demo media, once
git -c core.autocrlf=false archive --format=tar HEAD > socialhub.tar
tar rf socialhub.tar tools/seed/assets
scp -i ~/.ssh/socialhub_oracle socialhub.tar azureuser@<ip>:/tmp/
ssh -i ~/.ssh/socialhub_oracle azureuser@<ip> \
  'sudo mkdir -p /opt/socialhub && sudo chown $USER /opt/socialhub && tar xf /tmp/socialhub.tar -C /opt/socialhub'
```

## 3. Set up and start
```bash
ssh -i ~/.ssh/socialhub_oracle azureuser@<ip>
cd /opt/socialhub && bash infra/vm/setup-server.sh <site> media.<ip-with-dashes>.sslip.io --agree-le-tos
```
Installs Docker, adds 4 GB swap, writes `.env` with random secrets and memory limits sized to the
VM, gets one Let's Encrypt certificate for both hostnames (renewal hooks stop/start Nginx), builds
the jars and images on the VM and starts everything. Re-running it is safe.

## 4. Demo content
```bash
cd /opt/socialhub/tools/seed
SEED_GUEST_PASSWORD=$(grep ^DEMO_PASSWORD= ../../.env | cut -d= -f2) python3 seed_demo.py --base https://<site>
cd ../.. && set -a && . ./.env && set +a
python3 tools/seed/backdate.py --state tools/seed/.seed-state-<site>.json \
  --compose "--env-file .env -f docker-compose.yml -f docker-compose.full.yml -f docker-compose.vm.yml"
```
The setup script installs a daily cron job that re-posts stories and resets the guest profile.

## Operating
- Status: `sudo docker compose --env-file .env -f docker-compose.yml -f docker-compose.full.yml -f docker-compose.vm.yml ps`
- Grafana: `ssh -L 3000:127.0.0.1:3000 azureuser@<ip>` then http://localhost:3000
- Update: ship a new tar, delete `backend/*/target`, re-run the setup script.
- Demo login: "Continue as demo user" (account `guest`, password = `DEMO_PASSWORD` in `.env`).
