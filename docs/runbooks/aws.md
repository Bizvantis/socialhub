# Runbook: deploy SocialHub to AWS (Steps 44-45)

Target: **one EC2 instance running the full Docker Compose stack**, media in **S3**, secrets in
**SSM Parameter Store**, HTTPS via **Let's Encrypt**, deployments from **Jenkins**.
This is the cheapest production-shaped setup for a portfolio project. The "Scaling up" section
lists the managed services that would replace each container in a real company.

> Cost warning: an EC2 `t3.xlarge` (16 GB) costs roughly USD 0.17/hour (~USD 120/month) in
> ap-south-1. Stop the instance when you are not demoing, and do the cleanup at the end.

---

## 0. Account safety (once)

1. Enable **MFA** on the root user; never use root for daily work.
2. Create an IAM user (or IAM Identity Center user) with admin rights for yourself, with MFA.
3. **Billing -> Budgets**: create a monthly cost budget (e.g. USD 20) with an email alert at 80%.

## 1. S3 bucket for media

```bash
BUCKET=socialhub-media-<your-unique-suffix>
REGION=ap-south-1
aws s3api create-bucket --bucket $BUCKET --region $REGION \
  --create-bucket-configuration LocationConstraint=$REGION
# Private: block every form of public access (files are served only via pre-signed URLs).
aws s3api put-public-access-block --bucket $BUCKET --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
# Encryption at rest (SSE-S3) and HTTPS-only access.
aws s3api put-bucket-encryption --bucket $BUCKET --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
sed "s/REPLACE_WITH_BUCKET/$BUCKET/g" infra/aws/s3-bucket-policy.json > /tmp/policy.json
aws s3api put-bucket-policy --bucket $BUCKET --policy file:///tmp/policy.json
# CORS: browsers PUT uploads straight to S3, only from your site's origin.
#   edit AllowedOrigins in infra/aws/s3-cors.json first
aws s3api put-bucket-cors --bucket $BUCKET --cors-configuration file://infra/aws/s3-cors.json
# Remove abandoned multipart uploads after a day.
aws s3api put-bucket-lifecycle-configuration --bucket $BUCKET --lifecycle-configuration \
  '{"Rules":[{"ID":"abort-mpu","Status":"Enabled","Filter":{},"AbortIncompleteMultipartUpload":{"DaysAfterInitiation":1}}]}'
```

## 2. IAM role for the instance (no access keys anywhere)

1. IAM -> Roles -> Create role -> trusted entity **EC2**.
2. Create an inline policy from `infra/aws/ec2-role-policy.json` (replace `REPLACE_WITH_BUCKET`
   and `REPLACE_WITH_ACCOUNT_ID`). It allows only: object read/write/delete in the media bucket,
   reading `/socialhub/prod/*` parameters, and decrypting them.
3. Name it `socialhub-ec2`. The AWS SDK in media-service and video-worker picks up these temporary
   credentials automatically (`DefaultCredentialsProvider`), so `S3_ACCESS_KEY` stays empty.

## 3. Secrets in SSM Parameter Store

```bash
put() { aws ssm put-parameter --region $REGION --type SecureString --overwrite --name "/socialhub/prod/$1" --value "$2"; }
put MYSQL_ROOT_PASSWORD "$(openssl rand -base64 24)"
put REDIS_PASSWORD      "$(openssl rand -base64 24)"
put JWT_SECRET          "$(openssl rand -base64 48)"
put RABBITMQ_PASSWORD   "$(openssl rand -base64 24)"
put GRAFANA_PASSWORD    "$(openssl rand -base64 18)"
put MEDIA_BUCKET        "$BUCKET"
put AWS_REGION          "$REGION"
put PUBLIC_URL          "https://socialhub.example.com"
put S3_CORS_ORIGINS     "https://socialhub.example.com"
put BOOTSTRAP_ADMINS    "you@example.com"
put SMTP_HOST "email-smtp.$REGION.amazonaws.com"   # Amazon SES (verify your domain first)
put SMTP_USER "<ses-smtp-user>"; put SMTP_PASSWORD "<ses-smtp-password>"
```

`infra/aws/fetch-secrets.sh` turns these into `/opt/socialhub/.env` (mode 600) on every deploy.
Note: the per-service MySQL passwords in `infra/mysql-init/01-schemas.sql` are development values.
For production, change them there (or create the users by hand) and set `AUTH_DB_PASSWORD`,
`USER_DB_PASSWORD`, ... as parameters too.

## 4. Network and instance

1. Security group `socialhub-web`: inbound **80, 443 from 0.0.0.0/0**, **22 from your IP only**.
   Nothing else: databases, Kafka, Grafana etc. are not reachable from the internet.
2. Launch **Ubuntu 24.04, t3.xlarge, 40 GB gp3**, IAM role `socialhub-ec2`, that security group,
   and paste `infra/aws/ec2-user-data.sh` as user data. Allocate an **Elastic IP** and attach it.
3. DNS: point `socialhub.example.com` (A record) at the Elastic IP.

## 5. HTTPS certificate

```bash
ssh ubuntu@socialhub.example.com
sudo certbot certonly --standalone -d socialhub.example.com -m you@example.com --agree-tos -n
# renewal: certbot's systemd timer renews; reload nginx afterwards
echo '0 4 * * * root certbot renew --quiet --deploy-hook "docker exec socialhub-web nginx -s reload"' | sudo tee /etc/cron.d/certbot-socialhub
```

Replace `socialhub.example.com` in `infra/nginx/socialhub-tls.conf` with your domain.

## 6. Deploy

From Jenkins (recommended): set `REGISTRY`, `DEPLOY_HOST=ubuntu@socialhub.example.com`,
`PUBLIC_URL`, and the `registry-creds` / `ec2-ssh` credentials; run the pipeline on `main`,
approve the **Deploy** step.

By hand:
```bash
rsync -az docker-compose*.yml config-repo infra ubuntu@socialhub.example.com:/opt/socialhub/
ssh ubuntu@socialhub.example.com 'cd /opt/socialhub && TAG=<tag> REGISTRY=<registry> ./infra/aws/deploy.sh'
```

## 7. Smoke test and go live

```bash
./infra/aws/smoke-test.sh https://socialhub.example.com
```
Then sign up through the site, upload a short video and confirm it plays (the worker transcodes it).
Monitoring: `ssh -L 3000:localhost:3000 -L 9090:localhost:9090 ubuntu@socialhub.example.com`
then open http://localhost:3000 (Grafana, dashboard "SocialHub - Services overview").

## 8. Cost cleanup (when you are done)

```bash
aws ec2 terminate-instances --instance-ids <id>
aws ec2 release-address --allocation-id <eip-alloc-id>     # an unattached Elastic IP is billed
aws s3 rm s3://$BUCKET --recursive && aws s3api delete-bucket --bucket $BUCKET
aws ssm delete-parameters --names $(aws ssm get-parameters-by-path --path /socialhub/prod --query 'Parameters[].Name' --output text)
```
Also delete the IAM role, the security group, the DNS record and any EBS snapshots.

---

## Scaling up (what a company would change)

| Container today | Managed AWS service | Why |
|---|---|---|
| mysql | Amazon RDS for MySQL (Multi-AZ) | backups, failover, patching |
| redis | ElastiCache for Redis | replication, failover |
| kafka | Amazon MSK | managed brokers, multi-AZ |
| rabbitmq | Amazon MQ for RabbitMQ | managed broker |
| elasticsearch | Amazon OpenSearch Service | managed cluster |
| services on one host | ECS Fargate or EKS behind an ALB | per-service scaling, rolling deploys |
| video-worker | ECS tasks scaled on queue depth | pay for transcoding only when busy |
| S3 downloads | CloudFront with signed URLs/cookies | edge caching for video segments |
| Nginx + certbot | ALB + ACM certificates | managed TLS |
