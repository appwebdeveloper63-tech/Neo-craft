# How to Auto-Build NeoCraft APK with GitHub Actions

Follow these steps ONCE. After that, every time you push code,
GitHub automatically builds and saves your APK.

---

## Step 1 — Create a free GitHub account
Go to https://github.com and sign up (free).

---

## Step 2 — Create a new repository
1. Click the **+** button (top right) → **New repository**
2. Name it: `NeoCraft`
3. Set to **Public** (free) or **Private**
4. Click **Create repository**

---

## Step 3 — Upload the code

### Option A — GitHub website (easiest, no Git needed)
1. On your new repo page, click **uploading an existing file**
2. Drag and drop the entire `NeoCraft_v8` folder contents
3. Click **Commit changes**
4. GitHub Actions starts building immediately ✅

### Option B — Git command line
```bash
cd NeoCraft_v8
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/NeoCraft.git
git push -u origin main
```

---

## Step 4 — Add your API keys as Secrets
(So they never appear in your code)

1. Go to your repo → **Settings** → **Secrets and variables** → **Actions**
2. Click **New repository secret** for each key:

| Secret Name         | Value                        |
|---------------------|------------------------------|
| GEMINI_API_KEY      | your new Gemini key          |
| OPENAI_API_KEY      | your OpenAI key              |
| FIREBASE_API_KEY    | your Firebase web API key    |
| FIREBASE_PROJECT_ID | your Firebase project ID     |

> You can skip the AI keys if you don't want the AI assistant.
> The game will build and run fine without them.

---

## Step 5 — Watch the build
1. Go to your repo → **Actions** tab
2. You'll see "Build NeoCraft APK" running
3. Wait 3–5 minutes for it to finish
4. Click the completed run → scroll down to **Artifacts**
5. Click **NeoCraft-debug-XXXXXXXX** to download your APK ✅

---

## Step 6 — Install APK on your phone
1. Send the APK file to your Android phone (email, WhatsApp, USB)
2. On your phone: **Settings → Security → Install unknown apps → Allow**
3. Open the APK file → Install → Play!

---

## Every future update
Just push your code changes to GitHub.
The APK rebuilds automatically. New download appears in Actions → Artifacts.

---

## Auto-release a version
To create a public download release:
```bash
git tag v1.0.0
git push origin v1.0.0
```
GitHub creates a Release page with a download link anyone can use.
URL format: `github.com/YOUR_USERNAME/NeoCraft/releases`

