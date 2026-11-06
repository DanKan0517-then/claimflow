# Deploying ClaimFlow

The whole system runs on one VM with Docker Compose. Oracle Cloud's **Always Free** tier is enough: an Ampere A1 VM with up to 4 OCPU / 24 GB RAM, free permanently.

## 1. Create the VM (Oracle Cloud)
1. Sign up at cloud.oracle.com (a card is needed for verification; Always Free resources aren't charged).
2. **Compute → Instances → Create instance**
   - Image: **Ubuntu 24.04**
   - Shape: **VM.Standard.A1.Flex**, 2 OCPU / 12 GB is plenty
   - Add your SSH public key
3. **Networking → your VCN → Security List → Add Ingress Rules:** TCP **80** and **443** from `0.0.0.0/0`.
4. On the VM, open the same ports in the OS firewall (Oracle's Ubuntu image blocks them by default):
   ```bash
   sudo iptables -I INPUT 6 -p tcp --dport 80 -j ACCEPT
   sudo iptables -I INPUT 6 -p tcp --dport 443 -j ACCEPT
   sudo netfilter-persistent save
   ```

## 2. Point a domain at it
Any domain works. For a free one, use DuckDNS (`yourname.duckdns.org`): create the subdomain and set its IP to the VM's public IP. Wait until `ping yourname.duckdns.org` shows that IP.

## 3. Deploy
```bash
ssh ubuntu@<vm-ip>
git clone https://github.com/DanKan0517-then/claimflow.git && cd claimflow
./deploy/deploy.sh yourname.duckdns.org you@example.com
# first run installs Docker -> log out, log back in, run the same command again
```

The script:
1. generates `.env` with random secrets
2. gets a Let's Encrypt certificate
3. builds every image and starts the stack, waiting until all health checks pass
4. installs cron jobs for nightly backups and the nginx reload that picks up renewed certificates
5. runs the smoke test against the live HTTPS URL

To update after pushing to `main`, run `./deploy/deploy.sh <domain> <email>` again.

Optional: add `GROQ_API_KEY=...` to `.env` (free key from console.groq.com) for LLM analysis, then rerun the script.

## 4. Auto-deploy from GitHub (optional)
Add repository secrets `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_DOMAIN` and `DEPLOY_EMAIL`. The `deploy.yml` workflow then deploys every successful CI run on `main` over SSH.

## What's exposed
Only ports 80 (redirects to HTTPS) and 443. Postgres, Redis and the API replicas are on the internal Docker network. Grafana (3000) and Prometheus (9090) are bound to `127.0.0.1`, so reach them through an SSH tunnel:
```bash
ssh -L 3000:localhost:3000 ubuntu@<vm-ip>   # then open http://localhost:3000
```
