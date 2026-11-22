<p align="center">
  <img src="art/playstore-icon-512.png" width="120" alt="Slip Away icon">
</p>

<h1 align="center">Slip Away</h1>

<p align="center">
  A fake incoming call that looks exactly like a real one, for when you need a polite way out.
</p>

---

Stuck in a group, a party or a meeting you'd like to leave? Slip Away makes your phone ring with a call that looks and sounds like any other. You answer, say "sorry, I have to take this", and go.

Most fake-call apps draw their own copy of a call screen. It never quite matches the real one, so people notice. **Slip Away draws no screen of its own.** It hands the call to Android's calling system, and your phone's own dialer shows its normal ringing screen, ringtone, vibration and in-call screen. A Samsung shows Samsung's screen and a Pixel shows Google's, because it really is the phone's own screen.

## Contents

- [Features](#features)
- [Privacy](#privacy)
- [Install](#install)
- [Setup and settings](#setup-and-settings)
- [How to use it](#how-to-use-it)
- [Troubleshooting](#troubleshooting)
- [Working on the code](#working-on-the-code)
- [License](#license)

## Features

- **Your phone's real call screen:** the system dialer shows everything: ringing, Answer/Decline, in-call timer, Mute, Speaker, hang-up.
- **Any caller:** type a number or pick a contact. A saved contact shows their own name and photo.
- **A voice on the line:** after you answer, a voice reads lines you write, with pauses for your replies, or plays a recording you choose. It plays quietly through the earpiece, so it sounds like a real conversation to people nearby.
- **Ring on a timer:** now, or in 10 s, 30 s, 1, 3, 5 or 10 minutes. Set it before you walk in and lock your phone.
- **Discreet triggers:** a Quick Settings tile, and long-press shortcuts on the app icon.
- **Behaves like a real call:** if you don't answer, it becomes a missed call. It follows silent mode and Do Not Disturb, and it appears in your call history.

## Privacy

Slip Away has **no internet access**, no ads and no tracking. It asks for one permission: exact alarms, so timed calls arrive on time.

It **cannot** read your contacts, your call log or your real calls, and it **cannot** place calls. When you pick a contact, Android shares only that one contact's number. When you pick a recording, Android shares only that one file. Everything you set stays on your phone.

## Install

Slip Away needs **Android 10 or newer**.

Until there is a store release, build it yourself (see [Working on the code](#working-on-the-code)) or install an APK someone has built for you. To install an APK, open it on your phone and allow installing from that source when Android asks.

## Setup and settings

When you first open Slip Away, a **One-time setup** card walks you through this.

### 1. Switch on Slip Away's calling account (required)

**What it is:** Android lets apps add "calling accounts", the same mechanism internet-calling apps use to make their calls look like normal phone calls. Slip Away registers one.

**Why it's necessary:** this is how Slip Away gets your phone's real call screen. Android only shows calls from calling accounts that **you** have switched on, so that no app can make your phone ring without your say-so. Until you switch it on, Slip Away can't ring at all.

**What it allows:** only incoming fake calls from Slip Away. It doesn't let Slip Away make calls, see your real calls, or change how your SIM calls work. Normal outgoing calls still go straight out on your SIM.

**How to switch it on:**

1. In Slip Away, tap **Open calling accounts**.
2. Tap **All calling accounts**.
3. Turn on the account with **your SIM's name** (for example "airtel") and a **plain phone icon**. Your real SIM appears with a SIM-card icon; leave that one alone.
4. Go back to Slip Away and tap **Try a test call**. Your phone rings straight away. Once that works, setup is done and the card disappears.

> **Why does it have my SIM's name?** Some dialers, Google's Phone app among them, show "Incoming call via ⟨account name⟩" once a phone has more than one calling account. Real calls then also say "via ⟨your SIM⟩", so giving Slip Away's account the same name makes the two look identical. You can change the name under **Who's calling → Calling account name**. Leave it empty to use the SIM's name.

If the **Open calling accounts** button doesn't open anything, look for it yourself. It's usually in **Settings → Apps → Default apps**, or in your **Phone app → Settings → Calling accounts**, depending on the phone.

### 2. Allow exact timing (recommended)

**What it is:** Android's **Alarms & reminders** permission (`SCHEDULE_EXACT_ALARM`).

**Why it's necessary:** to save battery, Android delays most app timers while the screen is off, sometimes by several minutes. With exact timing, "ring me in 3 minutes" rings in 3 minutes even with the phone asleep in your pocket. Without it, timed calls still come, just possibly late. "Ring now" isn't affected.

**How to allow it:** tap **Allow exact timing** on the card in Slip Away, then turn on **Allow setting alarms and reminders**. The card goes away once it's allowed. (On some phones it's already allowed and the card never appears.)

### 3. Quick Settings tile (optional)

**Why:** the fastest way to start a call. Swipe down, tap once, and it rings after your chosen delay.

**How:** in Slip Away, tap **Add the Quick Settings tile** (Android 13+). On older versions, swipe down twice, tap the pencil/edit button, and drag **Slip Away** into your tiles.

### 4. Things Slip Away doesn't need

- **Contacts permission:** not needed. The contact picker shares only the one number you choose.
- **Storage permission:** not needed. The file picker shares only the one recording you choose.
- **Microphone, location, notifications, internet:** never used.
- **Battery optimisation exemption:** not needed on most phones (see [Troubleshooting](#troubleshooting) if timed calls never arrive).

### Text-to-speech (for the spoken voice)

The "voice reads my lines" option uses your phone's built-in text-to-speech engine. Most phones have one already (on Android it's usually **Speech Services by Google**). If you hear nothing, check **Settings → Accessibility → Text-to-speech output**: make sure an engine is installed and its language matches your lines.

## How to use it

### Choose who's calling

Under **Who's calling**:

- **Phone number:** the number shown on the call. **Use one that's saved in your contacts** (a family member, a friend). Your dialer then shows their real name and photo, which is the most convincing option. **Choose from contacts** fills it in for you.
- **Name, if the number isn't a contact:** a name to show instead of a bare number. Some dialers ignore it, and Google's Phone app shows it as "Likely: ⟨name⟩", the way it shows caller ID from the network. A saved contact looks better.
- **Calling account name:** see [setup step 1](#1-switch-on-slip-aways-calling-account-required).

### Choose what happens after you answer

Under **After you answer**:

- **A voice reads my lines:** write what the "caller" says, one line per line. After each line there's a 3.5-second pause for your reply. **Reset to the example** brings back the sample script.
- **Play a recording:** pick any audio file. A tip: ask a friend to record "Hey, can you come now? It's urgent", leaving gaps for your answers.
- **Silence:** nothing plays, and you do the talking.

The sound comes through the **earpiece**, like a real call. If the phone app switches to speaker, tap **Speaker** to switch it back.

### Start a call

Pick a delay under **Ring me** (**Now**, 10 s, 30 s, 1 min, 3 min, 5 min or 10 min) and tap **Ring**. While it counts down you can lock your phone, or tap **Cancel**.

Other ways to start one without opening the app:

| Trigger | How | Delay used |
|---|---|---|
| Quick Settings tile | Swipe down, tap **Slip Away**. Tap again to cancel. | The delay last chosen in the app |
| App shortcuts | Long-press the Slip Away icon: **Call in 10 s**, **1 min** or **3 min**. Drag one to your home screen for a one-tap button. | 10 s / 1 min / 3 min |

### During the call

It's a normal call to your phone: answer, decline, mute, hold and hang up as usual. If you don't answer within 45 seconds, it stops ringing and shows as a missed call.

### Tips for a convincing exit

- Set the timer **before** you sit down, then forget about it.
- Use a contact people would believe, like a parent, a flatmate or your partner.
- Keep the ringer on. Silent mode and Do Not Disturb mute Slip Away just like a real call.
- The call stays in your call history like a real one, in case someone glances at your phone later.

## Troubleshooting

| Problem | Fix |
|---|---|
| "Finish setup first" / "still switched off" | Switch on Slip Away's calling account ([setup step 1](#1-switch-on-slip-aways-calling-account-required)), then tap **Try a test call**. |
| No sound when it rings | The phone is on silent or Do Not Disturb, the same as for a real call. |
| No voice after answering | Check the text-to-speech engine ([see above](#text-to-speech-for-the-spoken-voice)), turn up the in-call volume during the call, and make sure **Silence** isn't selected. |
| Timed call came late | Allow exact timing ([setup step 2](#2-allow-exact-timing-recommended)). |
| Timed call never came (some Xiaomi, Oppo, Vivo, Huawei phones) | These phones can stop apps in the background. Set **Settings → Apps → Slip Away → Battery** to **Unrestricted / No restrictions**. |
| The call screen shows a name I don't want | Change **Calling account name** to match your SIM's name. |

## Working on the code

Contributions are welcome: bug fixes, support for more phones, translations, new triggers.

### What you need

- [Android Studio](https://developer.android.com/studio) (recent version), or the command-line Android SDK
- JDK 17 or newer (Android Studio includes one)
- Android SDK Platform **37**
- An Android 10+ phone with **USB debugging** on. Use a real phone if you can: the dialer behaviour is the whole point, and emulators only show the stock dialer.

### Step by step

1. **Fork** this repository on GitHub, then clone your fork:

   ```bash
   git clone https://github.com/<your-username>/slipaway.git
   cd slipaway
   ```

2. **Open it in Android Studio** (*File → Open* → the `slipaway` folder) and let Gradle sync. Android Studio creates `local.properties` with your SDK path.

   On the command line instead, tell Gradle where the SDK is:

   ```bash
   echo "sdk.dir=$HOME/Android/Sdk" > local.properties
   ```

3. **Build** a debug APK:

   ```bash
   ./gradlew assembleDebug
   ```

   The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. If Gradle can't find Java, point it at Android Studio's bundled JDK, for example `JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug` on Linux.

4. **Install** on your phone (plugged in over USB):

   ```bash
   ./gradlew installDebug
   ```

   Or press **Run ▶** in Android Studio.

5. **Create a branch** for your change:

   ```bash
   git checkout -b my-change
   ```

6. Make your change, then **check it** on a real phone (see [Testing a change](#testing-a-change)) and run lint:

   ```bash
   ./gradlew lintDebug
   ```

7. **Commit, push and open a pull request** against `main`. Say what you changed and which phones you tested on.

### How it works

```
You tap Ring / the tile / a shortcut
        │
        ▼
CallTrigger ──(delay?)──► AlarmManager ──► RingReceiver
        │                                     │
        └──────────────┬──────────────────────┘
                       ▼
TelecomManager.addNewIncomingCall(Slip Away's calling account)
                       │
                       ▼
Android Telecom binds FakeCallService ──► creates a FakeConnection (ringing)
                       │
                       ▼
The phone's own dialer shows the call  ──► user answers ──► VoicePlayer speaks
```

- **`CallAccount`** registers a `PhoneAccount` with `CAPABILITY_CALL_PROVIDER`. It declares only a private URI scheme (`slipaway:`), not `tel:`, so Android never offers it for outgoing calls, and dialers don't start asking "call with which account?".
- **`FakeConnection`** is a `Connection` in VoIP audio mode, so the app's own audio reaches the earpiece. It turns into a missed call after 45 s.
- **`VoicePlayer`** renders text-to-speech to WAV files and plays them from Slip Away's own process with `USAGE_VOICE_COMMUNICATION`. Android 15+ restricts audio from background apps, and the speech engine counts as one.

### Code map

| File | What it does |
|---|---|
| `app/src/main/java/dev/slipaway/call/CallAccount.kt` | Registers the calling account, its name, and whether it's switched on |
| `.../call/CallTrigger.kt` | Rings now or schedules an alarm. Every trigger goes through here |
| `.../call/RingReceiver.kt` | Receives the alarm and rings |
| `.../call/FakeCallService.kt` | The `ConnectionService` Android binds to for each call |
| `.../call/FakeConnection.kt` | One call: ringing, answer, hold, hang-up, missed-call timeout |
| `.../call/VoicePlayer.kt` | Spoken lines or a recording, through the earpiece |
| `.../tile/RingTileService.kt` | Quick Settings tile |
| `.../TriggerActivity.kt` | Invisible activity behind the app-icon shortcuts |
| `.../Prefs.kt` | All settings (SharedPreferences) |
| `.../ui/MainScreen.kt` | The app's single screen (Jetpack Compose) |
| `app/src/main/res/xml/shortcuts.xml` | App-icon shortcuts |
| `art/` | Icon master artwork (SVG) and Play Store icon |

### Testing a change

Android's calling system behaves differently between phone brands, so test the call itself, not just the app screen:

- [ ] **Test call:** after setup, **Try a test call** rings.
- [ ] **Ringing screen:** it looks like a normal incoming call, and a saved contact shows their name and photo.
- [ ] **Voice:** after answering, the voice is audible in the earpiece.
- [ ] **Timed call:** a 10 s call rings with the phone locked.
- [ ] **Missed call:** an unanswered call becomes a missed call after 45 s.
- [ ] **Outgoing calls:** a normal call from the Phone app goes straight out, with no account prompt.

Useful commands while debugging:

```bash
adb logcat -s SlipAway                          # Slip Away's own log messages
adb shell dumpsys telecom | grep -A2 slipaway   # Is the calling account registered? [X] = switched on
```

Tested so far: **Motorola g85, Android 16, Google Phone app**. If you test on another phone, please add it here in your pull request.

### Notes for contributors

- **Changing the package name:** to publish your own build, change `applicationId` in `app/build.gradle.kts` **and** `android:targetPackage` in `app/src/main/res/xml/shortcuts.xml`. They must match, or the app-icon shortcuts break.
- **Release builds:** `./gradlew assembleRelease` signs with the debug key until you add your own keystore. That's fine for your own phone, but never publish a debug-signed build.
- **Reading the calling account on Android 16:** Android 16 refuses `TelecomManager.getPhoneAccount()` unless the app holds `READ_PHONE_NUMBERS`, even for its own account. Slip Away avoids asking for that permission. Instead it learns whether the account is on from whether Android accepts a call (see `CallAccount.isEnabled` and `CallTrigger.ringNow`). Please keep the permission list short.
- **Play Store:** apps that register calling accounts get extra review, so describe the purpose plainly in any store listing.

## License

Slip Away is free software, released under the **GNU General Public License v3.0**. See [LICENSE](LICENSE) for the full text.

In short, you may use, study, change and share it, including your changed versions. If you distribute a changed version, it must also be under GPL-3.0, with its source code available.

Copyright © 2026 Abhilesh Singh
