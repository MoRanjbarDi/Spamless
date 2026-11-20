# Spamless 1.1.1

Spamless is an Android SMS/MMS app with regular-expression spam filtering and optional message encryption. This fork uses its own public version numbering. The current release is **1.2.2**.

## Inbox and search

- **All**, **Contacts**, and **Others** tabs appear below search. Choose the opening tab in **Settings → General → Default inbox tab**. All is the initial default.
- **Pull down to refresh** the inbox, including an empty tab. Refresh reloads contacts and rebuilds the selected filter, so a newly saved contact moves out of Others and into Contacts. Contact changes also refresh when the app resumes and when the contacts observer reports an update.
- Contacts includes conversations with at least one saved contact. Others contains conversations with no saved contacts.
- Search returns individual matching messages, newest first, with a highlighted excerpt. Opening a result jumps to that message.
- Search follows the selected tab or folder, including Spam and Archived.
- The Spam folder supports **Select all** for bulk actions.
- An inbox conversation has a **Show spam messages** menu option, off by default and saved per conversation. It changes visibility only; spam keeps its original classification and is labelled in the combined view.

## Trash

- Deleted SMS and MMS messages go to **Trash** by default, including individual deletions, conversation deletions, notification actions, timed deletion, and general/spam automatic deletion.
- The retention period starts **when a message is deleted**, not when it was received. The default is **5 days**; choose 1–3650 days in **Settings → General → Keep deleted messages**.
- Trash supports **Restore**, **Restore all**, **Delete permanently**, and **Empty Trash**. Permanent deletion asks for confirmation. Restoration returns messages to their conversation using the current spam rules and preserves MMS attachments. Restored messages are read and do not generate a new arrival alert.
- **Use Trash** can be disabled. Future deletions then become permanent; messages already in Trash keep their retention period. Shortening the retention period asks for confirmation before applying the new limit to existing Trash.
- Trashed messages are excluded from inbox/spam/archive previews, conversation contents, search, notifications, gallery lists, and newly created message backups. Full and incremental synchronization preserve Trash timestamps.
- Cleanup runs at app startup, when Trash opens, after a retention change, and through a persisted hourly background job. Android controls when background jobs actually run, particularly during Doze or while the app is stopped.

Trash state is app-local. System SMS/MMS records and attachments are retained until permanent deletion to support restoration. Other SMS apps can see those retained records; removing app data or reinstalling without its data removes the local Trash markers. Provider deletions outside Spamless are not intercepted.

## Spam controls

The built-in Iranian advertising rule and the optional Java regular expression continue to work together. Spam settings now have icons and include:

- **Spam notifications:** Off, Dismiss after 10 seconds, or On.
- **Spam notification sound:** Silent, Beep, or the configured SMS notification sound. The sound choice is independent of visual notifications: Off can still play the chosen sound when a new spam message arrives. Select Silent to mute it. Device volume and Do Not Disturb still apply.
- **Delete spam messages automatically:** choose an age in days using the same dialog as general automatic deletion, or choose Never. The default is Never. A confirmation is shown before immediately deleting existing expired messages.

Spam-only cleanup removes only expired spam, including in mixed-sender threads. It preserves normal messages and recent spam. Cleanup runs after saving an enabled policy and then through Android's hourly background job; Android controls the exact background execution time. The separate General deletion policy still applies to all messages, so an enabled general policy can also remove spam.

## Defaults and settings

- Blue is the default theme and delivery confirmations are enabled by default. Explicitly saved user choices remain intact; delivery reports also depend on carrier support.
- **Encryption settings** contains the three encryption controls, after General and immediately before Sync.
- The Pure black night mode option is removed from Settings. Ordinary night mode remains available.
- Google Play rating prompts are disabled.
- Donate and Invite friends are hidden in the navigation drawer. Their backend handlers remain for future use.
- The launcher preserves its original geometry with an exact HSV hue rotation of -130 degrees. Spam has a distinct warning-tray icon.
- Credits and links are updated, including the exact requested `New in QKSMS` changelog title. Original legal notices and technical package/dependency names are retained.

## Android compatibility and storage

The application targets **Android 16 / API 36** and supports Android 5.0 / API 21 and later. It includes current notification permission handling, explicit receiver exports and PendingIntent mutability, system/keyboard insets, and foreground restore-service declarations. Scheduled operations use an OS-supported fallback when exact-alarm access is unavailable.

On Android 11 and later, new backups are saved through MediaStore in **Downloads/Spamless/Backups**, without broad storage permission. They remain in Downloads after uninstall. Use **Restore → Choose another file** to select a legacy or externally stored JSON backup through Android's file picker. Earlier Android versions retain the filesystem backup directories, including legacy QKSMS backups. Viewed/shared message attachments use app-owned temporary/export files on newer Android.

A current target SDK addresses the outdated-Android target warning. Android, Play Protect, and device manufacturers independently control checks for sideloaded or unverified applications; an APK cannot promise to eliminate every installation prompt.

## Build

Requirements:

- JDK 11.
- Android SDK platform 36.
- Android SDK Build-Tools **29.0.3** and **36.0.0**. The pinned legacy Android plugin uses 29.0.3 for packaging; the project uses AAPT2 and the Java 11 D8 API from 36.0.0 for current platform resources and bytecode. Java 21 command-line wrappers are excluded from the Gradle compiler cache.
- The included Gradle 6.7.1 wrapper.

```sh
export JAVA_HOME=/path/to/jdk-11
export ANDROID_HOME=/path/to/android-sdk
export REALM_DISABLE_ANALYTICS=true
export SPAMLESS_KEYSTORE=/private/path/spamless-release.jks
export SPAMLESS_KEY_ALIAS=spamless
export SPAMLESS_KEYSTORE_PASSWORD='your-keystore-password'
./gradlew :presentation:assembleNoAnalyticsRelease -x :presentation:lintVitalNoAnalyticsRelease
```

The wrapper scripts disable Realm build-plugin telemetry. Set the same environment variable for IDE builds. After dependencies have been cached, `--offline` can be used to build without dependency-network access.

Output: `presentation/build/outputs/apk/noAnalytics/release/Spamless-v1.2.2-noAnalytics-release.apk`.

The delivered APK is a signed **noAnalytics release** with application ID **`com.moranjbardi.spamless`**, not a debug APK. Debug builds use `com.moranjbardi.spamless.debug`. Release APKs are unsigned if the signing environment variables are omitted. Keep the separately delivered signing backup private and use the same key for future updates; it is intentionally excluded from this source archive. The legacy lint-vital task is skipped in the packaging command above; compilation, resource linking, shrinking and signing still run.

The public version is 1.2.2 and the internal Android versionCode is 4014. Updating an installed app requires the same application ID and signing certificate. The app now has its own Android identity, `com.moranjbardi.spamless`. It installs separately from both the previous `org.cpartisans.psms` release and the old `com.moez.QKSMS` debug app. The same private Spamless release signing key is retained; the previous signing backup is still valid. System SMS/MMS can be synchronized after selecting Spamless as the default SMS app, while private settings and encryption keys belong to each app package and must be transferred separately. Back up important app data before switching packages. This fork key cannot sign updates over an upstream APK signed by someone else.

## Tests

```sh
./gradlew :domain:testDebugUnitTest :data:assembleNoAnalyticsDebugAndroidTest :presentation:assembleNoAnalyticsDebugAndroidTest
./gradlew :data:connectedNoAnalyticsDebugAndroidTest
./gradlew :presentation:connectedNoAnalyticsDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.moez.QKSMS.common.util.SpamNotificationTest
```

Release 1.1.1 is packaged without running unit tests, instrumentation tests, or emulator checks, as requested. APK metadata and signing are inspected during packaging.

Run connected tests on an emulator or test device. Database tests use in-memory Realm databases and a fake SMS provider. Notification tests use synthetic messages and cover hidden notifications, independent sound choices, ten-second expiration, and normal notifications. The existing retention fixtures explicitly disable Trash to continue exercising permanent-deletion behavior.

The bundled native Realm dependency remains pinned at 10.4.0. Native behavior on devices using 16 KB memory pages needs separate validation before distribution to those devices.

## Credits and license

Moranjbardi, Cpartisans, Moez Bhatti. Based on [Partisan-SMS](https://github.com/wrwrabbit/Partisan-SMS) and [QKSMS](https://github.com/moezbhatti/qksms).

- Source and releases: [MoRanjbarDi/Spamless](https://github.com/MoRanjbarDi/Spamless)
- Contact: [moranjbardi.github.io](https://moranjbardi.github.io/)
- Copyright: © 2014–2026
- GNU General Public License v3.0; original GPL notices are preserved. See [LICENSE](LICENSE).

## 1.2.0 security and inbox improvements

- Every send path, including notification replies and the quick-reply window, now applies the conversation's encryption policy in the shared transport.
- New encrypted text uses the versioned `SL2:` AES-256-GCM format with a cryptographically random 96-bit nonce, a 128-bit authentication tag and authenticated protocol metadata. Both participants need Spamless 1.2.0 or newer. Legacy sending/encoding controls are retired. Old formats are decoded only for displaying messages dated before this installation's protocol upgrade; newly received legacy messages do not use the compatibility decoder. Existing scheduled messages created before updating should be cancelled and recreated, since their bodies may already contain legacy ciphertext.
- Shared preferences wrap the global message key with a local storage key protected by Android Keystore. The complete Realm database, including conversation keys and scheduled drafts, is encrypted with a separate Keystore-wrapped key. Existing local data migrates on startup. System cloud backup and device-transfer extraction exclude private app data; wrapped storage keys live in the no-backup directory. Keep copies of your shared conversation keys somewhere secure before clearing app data or uninstalling, because the device-bound storage keys cannot be restored on another device. Manual SMS exports retain their existing behavior.
- Search copies only matching message and conversation data, limits object-graph depth, interrupts superseded searches and displays **Searching…** during debounce and lookup. No result-count cap is imposed.
- **Vibrate for spam messages** is independent of spam sound and visual notification mode, defaults on, and respects silent mode and Do Not Disturb. Android notification-channel settings also apply to visible alerts.
- In Spam, select a thread and choose **Always allow this sender** to move current messages to the inbox and allow future messages from that sender. Select individual spam messages and choose **Mark as not spam** to move just those messages. Exceptions survive provider resynchronization and changes to regex rules.

The reset-by-SMS feature, receiver exposure, unencrypted MMS attachments, Trash retention and manual export policies were not changed in this release (review findings S5–S9).


## 1.2.1 responsiveness fixes

- **Always allow this sender** and **Mark as not spam** move the affected messages directly in a single transaction. They no longer reclassify the entire inbox for each action. Sender and message exceptions remain persistent, selections are captured before background work starts, and the app shows success/failure feedback.
- Contact refresh builds indexes for number matching, computes changes before taking the database write lock, and updates only changed contacts and recipient links. An unchanged address book causes no database writes. Pull-to-refresh keeps its spinner tied to completion/error/disposal and preserves the existing list query. Redundant initial contact-change events are removed.
- Moving messages to Trash updates their conversation rows in the same commit, before alarm cancellation or provider updates. Provider read-state updates and permanent deletion use batches of up to 250 messages. Deletion no longer deep-copies message/attachment graphs or refreshes the database separately for each selected message.
- Startup spam classification runs only when its rules/signature change. Classification scans do not hold the database write lock, and only changed conversation halves are rebuilt. Full provider imports still explicitly build both Inbox and Spam rows.

Release validation: Android `noAnalyticsRelease` compilation, release packaging and APK identity/signature inspection. No device/emulator or performance benchmark tests were run.


## 1.2.2 Arabic-Indic spam matching

The built-in Iranian advertising SMS rule now recognizes `لغو ١١` (Arabic-Indic digit one, U+0661), alongside `لغو 11` and `لغو ۱۱`. Existing colon, spacing and multiline behavior is preserved. The effective rule is `.*لغو[:\s]*(11|۱۱|١١).*`. Startup classification signatures now include the built-in rule so existing messages are reclassified when that rule changes; saved sender and individual-message exemptions still apply.
