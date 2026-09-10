# Deploying to Oracle Cloud Always Free

End result: `https://your-name.duckdns.org/swagger-ui.html`, live, on a machine that
costs nothing and does not expire.

Budget about three hours. Most of it is waiting.

---

## Part 1 — Oracle Cloud account

Go to https://www.oracle.com/cloud/free/ and sign up.

- **Home region matters and cannot be changed later.** Pick one close to Sri Lanka:
  Mumbai, Singapore, or Hyderabad. Mumbai usually has the best ARM availability.
- A credit or debit card is required for identity verification. Oracle places a
  temporary hold of about USD 1 and releases it. Always Free resources are never
  charged, but keep the account on the free tier rather than upgrading to
  pay-as-you-go.
- Verification takes anywhere from ten minutes to a few hours.

---

## Part 2 — SSH key

On Windows, in PowerShell:

```powershell
ssh-keygen -t ed25519 -C "clinic-api" -f $env:USERPROFILE\.ssh\oracle_clinic
```

Press Enter twice to skip the passphrase (or set one — you will type it on every
connection).

Two files appear in `C:\Users\skavi\.ssh\`:

| File | What it is |
|---|---|
| `oracle_clinic` | Private key. **Never share this.** |
| `oracle_clinic.pub` | Public key. This goes to Oracle. |

Copy the public key to your clipboard:

```powershell
Get-Content $env:USERPROFILE\.ssh\oracle_clinic.pub | Set-Clipboard
```

---

## Part 3 — Create the VM

In the Oracle console: **Menu → Compute → Instances → Create instance**.

| Field | Value |
|---|---|
| Name | `clinic-api` |
| Image | **Canonical Ubuntu 22.04** (click *Change image*) |
| Shape | **VM.Standard.A1.Flex** (click *Change shape* → **Ampere** tab) |
| OCPUs | **4** |
| Memory | **24 GB** |
| SSH key | *Paste public keys* → paste from your clipboard |
| Boot volume | 100 GB (still free) |

Leave networking on the defaults — it creates a VCP and a public subnet.

Click **Create**.

### If you see "Out of host capacity"

This is extremely common and not something you did wrong — the free ARM shape is in
high demand. Options, in order of how well they work:

1. **Just retry.** Capacity frees up constantly. Try every few hours.
2. **Change availability domain.** The create form has AD-1, AD-2, AD-3 (in regions
   that have them). Try each.
3. **Ask for less.** 2 OCPU / 12 GB often succeeds when 4/24 does not. It is still
   more than enough for this stack.
4. **Different region.** Only if you have not created resources yet, since your home
   region is fixed.

Once the instance is running, copy the **Public IP address** from the instance page.

---

## Part 4 — Open the ports

Oracle blocks traffic in **two** places, and this trips up almost everyone. You must
do both.

### 4a. Security List (Oracle's firewall)

Instance page → **Virtual cloud network** link → **Security Lists** → **Default
Security List** → **Add Ingress Rules**.

Add two rules:

| Source CIDR | IP Protocol | Destination Port |
|---|---|---|
| `0.0.0.0/0` | TCP | `80` |
| `0.0.0.0/0` | TCP | `443` |

Do **not** open 5432, 6379, 5672 or 15672. Those services are only reachable inside
the Docker network, which is exactly what you want.

### 4b. iptables (the VM's own firewall)

Oracle's Ubuntu image ships with iptables rules that drop everything except SSH. The
Security List change alone will not get traffic through. SSH in first:

```powershell
ssh -i $env:USERPROFILE\.ssh\oracle_clinic ubuntu@YOUR_PUBLIC_IP
```

Then, on the VM:

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

If `netfilter-persistent` is missing:

```bash
sudo apt-get update && sudo apt-get install -y iptables-persistent
```

Without the save step the rules disappear on reboot, and the site silently stops
working weeks later.

---

## Part 5 — Install Docker

Still on the VM:

```bash
sudo apt-get update && sudo apt-get upgrade -y
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
```

Log out and back in for the group change to apply:

```bash
exit
```

```powershell
ssh -i $env:USERPROFILE\.ssh\oracle_clinic ubuntu@YOUR_PUBLIC_IP
```

```bash
docker --version
docker compose version
docker run --rm hello-world
```

### Swap

24 GB of RAM does not need swap, but a build spike with no swap kills the process
outright rather than slowing it down. Two gigabytes of insurance:

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

---

## Part 6 — Free subdomain

Caddy gets a TLS certificate from Let's Encrypt, which needs a real hostname. A bare
IP address will not do.

1. Go to https://www.duckdns.org and sign in with GitHub or Google.
2. Type a subdomain — `kavindu-clinic`, for example — and click **add domain**.
3. In the **current ip** box, put your VM's public IP, and click **update ip**.

You now have `kavindu-clinic.duckdns.org`.

Confirm DNS has propagated before continuing, from PowerShell:

```powershell
nslookup kavindu-clinic.duckdns.org
```

It must return your VM's IP. If it does not, wait a few minutes. Requesting a
certificate before DNS resolves burns one of your five weekly Let's Encrypt attempts.

*(If you buy a real domain later — Namecheap, Cloudflare, around USD 10/year — just
point an A record at the IP and change `PUBLIC_HOST`. Everything else is identical.)*

---

## Part 7 — Deploy

On the VM:

```bash
git clone https://github.com/kavindu116/clinic-booking-api.git
cd clinic-booking-api/deploy
cp .env.prod.example .env
```

Generate three separate secrets:

```bash
openssl rand -base64 24   # POSTGRES_PASSWORD
openssl rand -base64 24   # RABBITMQ_PASSWORD
openssl rand -base64 48   # JWT_SECRET
```

```bash
nano .env
```

Fill in every `REPLACE_ME`, plus `PUBLIC_HOST` and `ACME_EMAIL`. Save with
`Ctrl+O`, `Enter`, then exit with `Ctrl+X`.

**Use different secrets from your development machine.** If your laptop is ever
compromised, production should not fall with it.

Then:

```bash
./deploy.sh
```

The first build pulls the Maven image and every dependency — expect five to ten
minutes. Subsequent deploys are much faster because Docker caches the dependency
layer.

---

## Part 8 — Check it

```
https://kavindu-clinic.duckdns.org/swagger-ui.html
```

A padlock in the address bar means Caddy got its certificate. Also try:

```
https://kavindu-clinic.duckdns.org/actuator/health      → {"status":"UP"}
https://kavindu-clinic.duckdns.org/actuator/prometheus  → 404 (blocked, as intended)
http://kavindu-clinic.duckdns.org/                      → redirects to https
```

Then run through the API properly:

1. `POST /api/v1/auth/register` — create yourself a patient account
2. `POST /api/v1/auth/login` — copy the access token, click **Authorize**
3. `GET /api/v1/doctors` — **empty**, because seeding is off in production
4. Create an admin by hand (below), then add a doctor and their availability
5. `GET /api/v1/doctors/{id}/slots?date=...` and book one

### Creating the first admin

Seeding is disabled in production, so there is no admin account and no endpoint that
creates one — by design, since a public "make me an admin" route is a serious hole.
Register normally, then promote yourself directly in the database:

```bash
docker exec -it clinic-postgres psql -U clinic -d clinicdb \
  -c "UPDATE users SET role = 'ADMIN' WHERE email = 'you@example.com';"
```

Log in again — the role is baked into the JWT, so the old token still says PATIENT.

---

## Part 9 — Housekeeping

### Nightly backup

```bash
crontab -e
```

```cron
0 2 * * * /home/ubuntu/clinic-booking-api/deploy/deploy.sh backup >> /home/ubuntu/backup.log 2>&1
```

Keeps the last seven dumps. A backup that has never been restored is a guess, so try
one restore into a scratch database at least once.

### Watching it

```bash
./deploy.sh status         # containers and health
./deploy.sh logs           # follow the app
./deploy.sh logs caddy     # TLS and proxy issues
docker stats               # memory and CPU
```

### Shipping a change

```bash
cd ~/clinic-booking-api/deploy
./deploy.sh
```

Pulls, rebuilds, restarts, waits for readiness.

---

## When something is wrong

**The page never loads.** Ports are the usual cause, and it is nearly always the
iptables half of Part 4 rather than the Security List half. Check from the VM:
`curl -I http://localhost` — if that works, the app is fine and the problem is the
firewall.

**No certificate / browser warning.** `./deploy.sh logs caddy`. Almost always DNS:
the hostname must resolve to this VM before Let's Encrypt will validate. Confirm with
`nslookup` from your laptop, not from the VM.

**App keeps restarting.** `./deploy.sh logs app`. Look for `APPLICATION FAILED TO
START` and read the block underneath — Spring's failure analyser usually names the
exact property.

**Out of memory during the build.** Add the swap file from Part 5, or build the image
on your laptop and push it to a registry instead.

**Everything worked, then stopped after a reboot.** The iptables rules were not
saved. Redo Part 4b including `netfilter-persistent save`.

---

## Where this is not production-grade

Worth being able to say out loud in an interview, because someone will ask:

- **One machine.** No redundancy. If the VM dies, the API is down until it comes back.
- **Database on the same box.** Fine here; a real deployment uses managed Postgres
  with automated backups and point-in-time recovery.
- **Deploys have downtime.** A few seconds while the container restarts. Rolling
  deploys need at least two app instances behind the proxy.
- **Metrics are collected but nothing scrapes them.** Prometheus and Grafana would
  be the next thing to add.
- **The notification consumer's idempotency cache is in memory.** Correct for a
  single instance, wrong the moment there are two — that needs Redis or a table.
- **The outbox retries at a flat interval.** Exponential backoff would be kinder to
  a broker that is genuinely down.

Knowing the limits of what you built is worth more than pretending there are none.
