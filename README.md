# Slip Away

An Android app that gives you a believable reason to leave: it makes your phone ring with an incoming call that looks exactly like a real one.

It doesn't draw its own copy of a call screen. Slip Away registers a **calling account** with Android's Telecom system, the way internet-calling apps do, and reports an incoming call through it. The phone's **own dialer** then shows its normal ringing screen, plays your real ringtone and vibration, and shows its normal in-call screen. That's Samsung's screen on a Samsung and Google's on a Pixel, with nothing copied.

## Features

- **Real call screen:** the system dialer draws everything.
- **Caller:** type a number or pick a contact. A saved contact's name and photo appear automatically.
- **After answering:** the phone's text-to-speech reads your lines through the earpiece with pauses for your replies, or plays a recording you pick, or stays silent.
- **Missed call:** if you don't answer within 45 seconds, it becomes a missed call, like a real one.
- **Triggers:**
  - Ring now, or after 10 s / 30 s / 1, 3, 5 or 10 min. Set it before you walk in and lock the phone.
  - A Quick Settings tile. One tap rings after your chosen delay; tap again to cancel.
  - Launcher shortcuts: long-press the icon for "Call in 10 s / 1 min / 3 min". You can drag these to the home screen.

## Build

The project uses Android Studio's bundled JDK:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. To install it:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## First run

1. Open Slip Away and tap **Open calling accounts**, then **All calling accounts**.
2. Switch on Slip Away's account. It has your SIM's name (for example "airtel") and a plain phone icon instead of a SIM-card icon.
3. Back in Slip Away, tap **Try a test call**.
4. Optional: allow **exact timing** so delayed calls arrive on the second while the phone sleeps.

## Tested so far

On a Motorola g85 with Android 16 and Google's Phone app:

- **Ringing:** Google's own incoming-call notification appears with the normal Answer and Decline buttons.
- **Contact lookup:** a saved number shows the contact's name and avatar.
- **Custom name:** a number that isn't a contact shows the custom name as "Likely: ⟨name⟩", Google's style for caller-ID names that don't come from contacts. Saved contacts look cleaner.
- **Account name:** the call says "Incoming call via airtel". The account copies the SIM's name. With two calling accounts, Google's Phone app labels real SIM calls "via airtel" too, so they match.
- **In-call screen:** Google's real in-call screen appears, with a timer, Keypad, Mute, Speaker and hang-up.
- **Voice:** spoken lines play one after another, with pauses between them.
- **Ending the call:** hanging up from the Phone app ends the call cleanly.
- **Delayed ring:** a delayed call rings full-screen while the phone is locked.
- **Missed call:** an unanswered call becomes a missed call.
- **Earpiece voice:** the voice is clearly audible.
- **Outgoing calls:** they still go straight out on the SIM, with no account prompt.

Things learned:

- **Reading the account on Android 16:** `TelecomManager.getPhoneAccount()` throws unless the app holds `READ_PHONE_NUMBERS`, even for the app's own account. Slip Away therefore learns whether the account is on from whether the system accepts a call. That's why setup ends with a test call.
- **"Would be muted" warnings:** Android 16 logs this for both the speech engine and Slip Away. It's the warn-only mode, and audio still plays. Lines are still rendered to files and played from Slip Away's own process, in case a later version enforces the rule.

## Still to test

- [ ] Calling back from call history doesn't go through Slip Away
- [ ] Samsung, Pixel and Xiaomi dialers
- [ ] Quick Settings tile and long-press shortcuts

## Code map

| File | What it does |
|---|---|
| `call/CallAccount.kt` | Registers the calling account and checks whether it's switched on |
| `call/CallTrigger.kt` | Rings now or schedules an alarm. The app, tile and shortcuts all go through it |
| `call/FakeCallService.kt` | The `ConnectionService` that Telecom binds to for the call |
| `call/FakeConnection.kt` | One call's lifecycle: ringing, answer, hold, hang-up, missed-call timeout |
| `call/VoicePlayer.kt` | Text-to-speech or an audio file, played through the earpiece |
| `tile/RingTileService.kt` | Quick Settings tile |
| `TriggerActivity.kt` | Invisible activity behind the launcher shortcuts |
| `ui/MainScreen.kt` | Setup, caller, voice and trigger settings |

## Known limits

- **Silent mode and Do Not Disturb** mute the call, as they would a real call.
- **Call history:** the call appears in it.
- **Play Store:** apps that register calling accounts get extra review. Describe the purpose plainly in the listing.
- **Signing:** release builds use the debug key until you add a keystore.
