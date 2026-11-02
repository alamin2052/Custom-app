# Agent Commander

An Android app where **you are the owner** of AI agents that operate your phone.

Each agent works in four stages:

1. **Learn mode** – you give it instructions, then do the task once on your phone. It watches (UI steps plus screenshots).
2. **Readiness check** – it tells you honestly whether it can do the job. If unsure, it asks you questions.
3. **Approval** – it shows its plan and does nothing until you tap **Approve and start**.
4. **Run** – it works step by step. If it is confused or something fails, it stops and asks you.

## Get the APK (about 5 minutes, free)

1. Create a free account at github.com and make a **new repository** (private is fine).
2. Upload the contents of this folder (including the hidden `.github` folder) to the repository.
   On a computer: `git init`, `git add .`, `git commit -m "Agent Commander"`, then push to the repo.
3. Open the repository, go to the **Actions** tab, and wait for **Build APK** to turn green (about 5 to 8 minutes). If it did not start, click **Build APK** then **Run workflow**.
4. Open the repository's **Releases** page (right sidebar), then **Latest build**, and download `app-debug.apk` on your phone.
5. Open the file and install it. Allow "install unknown apps" for your browser when asked.

Alternative: open this folder in Android Studio and press Run.

## First-time setup on the phone

1. Open **Agent Commander**, tap **Settings**.
2. Paste your **Anthropic API key** (console.anthropic.com) and tap Save.
3. Tap **Open accessibility settings**, find **Agent Commander**, switch it on.
   - If the switch is greyed out (apps installed from a file): tap **Open App info**, tap the three dots (top right), choose **Allow restricted settings**, then try again.
4. Allow notifications when asked (they hold the STOP button).

## Owner control (the agent cannot refuse any of these)

| Control | Where |
|---|---|
| Red **STOP EVERYTHING** | bottom of every screen |
| **STOP** / Pause / Resume | notification, works from any app |
| **Volume Up + Volume Down together** | hardware, any screen |
| Type `stop` | message box on the agent screen |
| Switch off the accessibility service | Android settings |

These are enforced in app code (`Engine.kt`), not by asking the AI nicely. The kill switch cancels the running job and the network request immediately.

Other safeguards enforced in code:
- Nothing runs without your approval of the plan. After an app restart no agent resumes by itself.
- Only one agent controls the screen at a time.
- Before every tap, the app re-checks that the agent is still allowed to run.
- Taps on risky buttons (pay, buy, send, delete, confirm, install, allow...) need your separate **Allow once** every time.
- Agents never type into password fields; they ask you to.
- Agents cannot operate this app, system Settings, or permission/installer screens.
- A step limit (default 60) makes the agent ask before continuing.
- Messages you send while it works make it drop its planned action and re-plan.
- The AI is told that text on screen is untrusted data (prompt-injection defence) and that your instructions override everything.

## Honest limits

- This is a debug-signed APK, fine for your own phone, not for the Play Store.
- Each action costs one AI call (with a screenshot), so tasks use API credit and run at human speed.
- Screenshots need Android 11+. Banking and other secure apps block screenshots and may hide their content from accessibility; the agent will ask you for help there.
- The agent can only do what the accessibility service can: taps, typing, scrolling, back/home, launching apps.
- Your API key is stored privately on the phone but not encrypted. Use a key with a spending limit.
- Start with harmless tasks and watch the first runs.
