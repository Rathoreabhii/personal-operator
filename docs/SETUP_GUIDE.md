# Personal Operator — Complete Setup Guide

> Step-by-step guide to deploy and run the system using your domain **veloe.in**.

---

## 📋 Prerequisites Checklist

- [x] Domain: `veloe.in`
- [x] Android Studio installed
- [x] Backend code ready at `c:\Users\Abhishek\cursor-free-vip\evo\backend`
- [ ] A VPS / cloud server (DigitalOcean, AWS, Railway, or Render)
- [ ] Android phone with USB cable

---

## Part 1: Run Locally First (Test Everything Works)

### 1.1 — Start the Backend

```powershell
cd c:\Users\Abhishek\cursor-free-vip\evo\backend
npm install
node src/index.js
```

You should see:
```
Server listening on port 3000
WebSocket ready at ws://localhost:3000/ws
```

### 1.2 — Test the API

Open a new terminal and run:
```powershell
Invoke-RestMethod -Uri http://localhost:3000/api/health -Headers @{"X-API-Key"="test-secret-key-dev-only"}
```

Expected: `status: ok`

### 1.3 — Test DeepSeek Integration

```powershell
$body = @{
    mode = "command"
    sender = "Abhishek"
    message = "Call Rahul and tell him meeting is shifted to 5 PM"
} | ConvertTo-Json

Invoke-RestMethod -Uri http://localhost:3000/api/process-message `
    -Method POST `
    -Headers @{"X-API-Key"="test-secret-key-dev-only"; "Content-Type"="application/json"} `
    -Body $body
```

Expected: A JSON response with `intent: "call_number"`, `human_text` in Hinglish, and `confirmation_required: true`.

✅ **If this works, your backend + DeepSeek are connected properly.**

---

## Part 2: Deploy Backend to Cloud

You need the backend running 24/7 on a server with a public URL. Here are 3 options (pick one):

### Option A: Railway (Easiest, Free Tier)

1. Go to [railway.app](https://railway.app) and sign up with GitHub
2. Click **"New Project"** → **"Deploy from GitHub Repo"**
3. Push your backend to GitHub first:
   ```powershell
   cd c:\Users\Abhishek\cursor-free-vip\evo
   git init
   git add .
   git commit -m "Initial commit"
   # Create a repo on github.com, then:
   git remote add origin https://github.com/YOUR_USERNAME/personal-operator.git
   git push -u origin main
   ```
4. In Railway, select the repo → set root directory to `backend`
5. Go to **Variables** tab → add all your `.env` values:
   ```
   PORT = 3000
   API_SECRET_KEY = (generate a strong random key)
   DEEPSEEK_API_KEY = sk-bc46889df116431dbbd16e7ba228c160
   DEEPSEEK_BASE_URL = https://api.deepseek.com/v1
   DEEPSEEK_MODEL = deepseek-chat
   NODE_ENV = production
   ```
6. Railway gives you a URL like `personal-operator-production.up.railway.app`
7. Go to **Settings → Networking → Custom Domain** → add `api.veloe.in`

### Option B: DigitalOcean Droplet ($6/mo)

1. Create a **Ubuntu 24.04** droplet at [digitalocean.com](https://digitalocean.com)
2. SSH into it:
   ```bash
   ssh root@YOUR_DROPLET_IP
   ```
3. Install Node.js:
   ```bash
   curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
   apt install -y nodejs nginx certbot python3-certbot-nginx
   ```
4. Clone your code:
   ```bash
   cd /opt
   git clone https://github.com/YOUR_USERNAME/personal-operator.git
   cd personal-operator/backend
   npm install --production
   ```
5. Create `.env`:
   ```bash
   nano .env
   # Paste your env variables (same as above)
   ```
6. Setup as a system service:
   ```bash
   nano /etc/systemd/system/operator.service
   ```
   Paste:
   ```ini
   [Unit]
   Description=Personal Operator Backend
   After=network.target

   [Service]
   Type=simple
   User=root
   WorkingDirectory=/opt/personal-operator/backend
   ExecStart=/usr/bin/node src/index.js
   Restart=always
   RestartSec=5
   Environment=NODE_ENV=production

   [Install]
   WantedBy=multi-user.target
   ```
   Then:
   ```bash
   systemctl enable operator
   systemctl start operator
   ```
7. Setup Nginx reverse proxy:
   ```bash
   nano /etc/nginx/sites-available/operator
   ```
   Paste:
   ```nginx
   server {
       listen 80;
       server_name api.veloe.in;

       location / {
           proxy_pass http://127.0.0.1:3000;
           proxy_http_version 1.1;
           proxy_set_header Upgrade $http_upgrade;
           proxy_set_header Connection "upgrade";
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_read_timeout 86400;
       }
   }
   ```
   Then:
   ```bash
   ln -s /etc/nginx/sites-available/operator /etc/nginx/sites-enabled/
   nginx -t
   systemctl restart nginx
   ```

8. Get free SSL certificate:
   ```bash
   certbot --nginx -d api.veloe.in
   ```

### Option C: Render (Free Tier)

1. Go to [render.com](https://render.com) → New Web Service
2. Connect your GitHub repo
3. Set root directory: `backend`
4. Build command: `npm install`
5. Start command: `node src/index.js`
6. Add environment variables (same as above)
7. Add custom domain `api.veloe.in`

---

## Part 3: Point Your Domain

Whichever cloud option you chose, you need to add a DNS record:

1. Go to your domain registrar (where you bought `veloe.in`)
2. Go to **DNS Settings**
3. Add a record:

| Type | Name | Value |
|---|---|---|
| **CNAME** | `api` | `your-cloud-url` (e.g., `personal-operator-production.up.railway.app`) |

Or if using DigitalOcean (IP address):

| Type | Name | Value |
|---|---|---|
| **A** | `api` | `YOUR_DROPLET_IP` |

4. Wait 5-10 minutes for DNS propagation
5. Test: open `https://api.veloe.in/api/health` in your browser — should show `{"status":"ok"}`

✅ **Your backend is now live at `api.veloe.in`**

---

## Part 4: Build & Install the Android App

### 4.1 — Open Project in Android Studio

1. Open Android Studio
2. **File → Open** → navigate to:
   ```
   c:\Users\Abhishek\cursor-free-vip\evo\android\PersonalOperator
   ```
3. Wait for Gradle sync (may take 2-5 minutes the first time)
4. If it asks to update Gradle or SDK — click **Update**

### 4.2 — Fix Default Server URL (point to your domain)

Before building, update the default server URL in the app.

Open `app/src/main/kotlin/com/evo/operator/security/SecureStorage.kt`

Find this line:
```kotlin
get() = prefs.getString(KEY_SERVER_URL, "ws://10.0.2.2:3000/ws") ?: "ws://10.0.2.2:3000/ws"
```
Change it to:
```kotlin
get() = prefs.getString(KEY_SERVER_URL, "wss://api.veloe.in/ws") ?: "wss://api.veloe.in/ws"
```

Also update the API key default (or set it at runtime in the app):
```kotlin
get() = prefs.getString(KEY_API_KEY, "") ?: ""
```
→ You can leave this empty and enter it in the app, or hardcode your key for dev builds.

### 4.3 — Enable USB Debugging on Phone

1. **Settings → About Phone** → tap **Build Number** 7 times → "Developer mode enabled"
2. **Settings → Developer Options** → enable **USB Debugging**
3. Connect phone via USB → tap **Allow** on the USB debugging prompt

### 4.4 — Build and Install

1. In Android Studio, select your phone from the device dropdown (top toolbar)
2. Click **Run ▶️** (green play button) or press `Shift + F10`
3. Wait for build (first build takes 2-5 minutes)
4. App installs and opens on your phone automatically

### 4.5 — Grant Permissions on Phone

When the app opens for the first time:

1. **Phone, Audio, Contacts** → tap **Allow** for each
2. **Overlay permission** → you'll be taken to Settings → toggle ON for "Personal Operator"
3. **Notification Access** (most important!):
   - You'll be taken to Settings → **Notifications → Notification access**
   - Find **"Personal Operator"** → toggle ON
   - Confirm the security warning
4. Go back to the app

### 4.6 — Configure the App

In the app's settings:
- **Server URL**: `wss://api.veloe.in/ws` (should be pre-filled if you changed the default)
- **API Key**: Enter the same `API_SECRET_KEY` you set in your backend `.env`
- **Your Name/Phone**: Enter your name or WhatsApp display name → this is how the app detects self-messages (command mode)

---

## Part 5: Test the Full Flow

### Test 1: Command Mode (Self-Message)

1. Open WhatsApp on your phone
2. Message yourself (WhatsApp → search your own name → send):
   ```
   Call Rahul and say meeting is shifted to 5 PM
   ```
3. Watch for the confirmation panel to slide in:
   ```
   🤖 "Main Rahul ko call karun aur bolu meeting 5 PM shift ho gayi? Confirm kar do."
   ```
4. Tap **Confirm** or **Reject**

### Test 2: Suggestion Mode (Others' Messages)

1. Ask a friend to send you a WhatsApp message like:
   ```
   Are you free tomorrow?
   ```
2. The app should show a suggestion overlay with a proposed reply
3. You can approve or dismiss

### Test 3: Kill Switch

1. In the app dashboard, toggle **Emergency Kill Switch** → ON
2. All operations immediately stop
3. Toggle OFF to resume

---

## 🔧 Troubleshooting

| Problem | Solution |
|---|---|
| App doesn't capture WhatsApp notifications | Check Settings → Notification access → Personal Operator is ON |
| "Connection failed" in app | Check server URL is correct, backend is running, and domain DNS is set |
| Gradle sync fails | Click **File → Invalidate Caches → Restart** in Android Studio |
| Build error about SDK | Install SDK 34 via **Tools → SDK Manager** |
| DeepSeek returns error | Check your API key is correct and has credit |
| Overlay not showing | Check Settings → Apps → Personal Operator → "Display over other apps" is ON |

---

## 📁 Quick Reference

| Item | Value |
|---|---|
| Backend URL | `https://api.veloe.in` |
| WebSocket URL | `wss://api.veloe.in/ws` |
| Health Check | `https://api.veloe.in/api/health` |
| Android Project | `evo\android\PersonalOperator` |
| Backend Code | `evo\backend` |
| Env Config | `evo\backend\.env` |
