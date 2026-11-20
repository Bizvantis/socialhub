# Runbook: public demo on Oracle Cloud (free)

One Oracle Cloud "Always Free" Ampere VM (4 OCPU, 24 GB RAM, arm64) runs the whole stack
(`docker-compose.yml` + `docker-compose.full.yml` + `docker-compose.oracle.yml`), with media in
SeaweedFS on the same VM. Cost: $0. For the AWS variant see [aws.md](aws.md).

## 1. Create the VM (Oracle console)
1. Compute -> Instances -> Create: image **Canonical Ubuntu 24.04**, shape **VM.Standard.A1.Flex**
   (4 OCPU / 24 GB), public subnet with a public IPv4, boot volume 100 GB, upload your SSH public key.
2. Networking -> your VCN -> Security Lists -> Default: ingress `0.0.0.0/0` TCP **80** and **443**.

The hostname needs no DNS: `<ip-with-dashes>.sslip.io` resolves to the IP
(e.g. `140-238-10-20.sslip.io`), and `media.<that>` serves the S3 endpoint.

## 2. Ship the code and build artifacts
```bash
mvn -f backend/pom.xml package                    # jars (CPU-independent)
python tools/seed/fetch_assets.py && python tools/seed/make_media.py   # demo media
git archive --format=tar HEAD > socialhub.tar
tar rf socialhub.tar backend/*/target/*-0.0.1-SNAPSHOT.jar tools/seed/assets
scp socialhub.tar ubuntu@<ip>:/tmp/ && ssh ubuntu@<ip> 'sudo mkdir -p /opt/socialhub && sudo chown ubuntu /opt/socialhub && tar xf /tmp/socialhub.tar -C /opt/socialhub'
```

## 3. Set up and start
```bash
ssh ubuntu@<ip>
cd /opt/socialhub && bash infra/oracle/setup-server.sh <ip-with-dashes>.sslip.io --agree-le-tos
```
Installs Docker, opens 80/443 in iptables, writes `.env` with random secrets, gets one Let's Encrypt
certificate for both hostnames (renewal hooks stop/start Nginx), builds the arm64 images and starts
everything. Re-running it is safe.

## 4. Demo content
```bash
cd /opt/socialhub/tools/seed
SEED_GUEST_PASSWORD=$(grep ^DEMO_PASSWORD= ../../.env | cut -d= -f2) python3 seed_demo.py --base https://<site>
set -a; . ../../.env; set +a
cd ../.. && python3 tools/seed/backdate.py --state tools/seed/.seed-state-<site>.json \
  --compose "--env-file .env -f docker-compose.yml -f docker-compose.full.yml -f docker-compose.oracle.yml"
```
A cron job (installed by the setup script) re-posts stories every morning and resets the guest profile.

## Operating
- Logs: `docker compose ... logs -f <service>`; Grafana: `ssh -L 3000:127.0.0.1:3000 ubuntu@<ip>`.
- Update: ship a new tar, then `docker compose ... up -d --build`.
- Demo login: the "Continue as demo user" button (account `guest`, password = `DEMO_PASSWORD` in `.env`).
