# Hacker's keyboard: Rebirth — privacy policy draft

Publication status: prepared 27 November 2026 for owner review. This tracked draft
has not replaced the live policy at https://baodeep.com/hkkpolicy. Set the effective
date when the reviewed text is published. The application package is
`com.baodeep.hackerskeyboard`; the Google Play developer named in the existing
policy is `adeepv`. Privacy contact: **priv@baodeep.com**.

## Typing and suggestions

The keyboard processes input and suggestions on the device and delivers the text
you enter to the app you are typing in. That receiving app handles the text under
its own practices. The keyboard has no Internet permission and does not send
keystrokes, typed text or dictionaries to a developer-operated service.

When learning is enabled, the keyboard can store learned words and adjacent word
pairs in its private app databases to improve suggestions. These are not a full
message transcript, but can contain sensitive words. Depending on settings and
usage, frequently learned words can automatically be added to Android's personal
dictionary. Words can also be added deliberately. The keyboard reads that shared
dictionary; other apps with appropriate access and the system provider may use it.

## Settings, add-ons and Android backup

Settings are stored in app preferences. Installed dictionary add-ons provide word
lists; the keyboard does not send typed text to those dictionary packages.
The optional keyboard notification helps open keyboard controls.

Android backup is enabled. The app's key-value backup agent supplies keyboard
settings. Whether backup occurs, where it is stored, and how it is restored depend
on Android, the device backup provider and your settings. Device migration and
other platform backup paths may vary; this policy does not guarantee that every
device excludes learned dictionaries. The developer does not operate the Android
backup service or have access to your backup through this app.

## External voice input and links

If you choose voice input, the keyboard switches to a separate enabled voice
input method. That app handles speech under its own policy and may use network
services. The keyboard does not request microphone permission or record audio
itself, and does not receive that voice input method's recognition result.
Some earlier releases included a recognizer-based fallback that could return
recognized text through the keyboard; that fallback is no longer included.

If you open this page from a link in the app, the link launches an external
browser only when you select it. Your browser then makes an HTTPS request to
baodeep.com; the web server necessarily receives connection metadata such as the
IP address. The keyboard does not attach typed text, dictionaries or editor
information to that request. The browser may provide additional request metadata
under its own settings and practices.

## Advertising and diagnostics

There are no ads, Advertising ID use, embedded analytics or embedded crash-reporting
SDKs in this app. The developer does not sell typing data or operate a service
collecting it. Android and Google Play may collect crash, ANR and device diagnostics
under their own settings and policies. The developer may view aggregated quality
information and diagnostic details available in Google Play Android vitals. This
is separate from an in-app telemetry SDK and is not a promise that the operating
system collects no diagnostics.

## Storage, retention and deletion

Android app-private storage protects the app's settings and learned databases from
ordinary access by other apps. These databases do not implement additional
app-level encryption. There is no fixed time-based retention period promised for
learned words; data can remain until removed, replaced or pruned during operation.

You can clear the app's storage in Android settings, or uninstall it, to remove its
local settings and learned databases. Words added or promoted to Android's personal
dictionary are separate: manage or delete them through the device's personal
dictionary controls. Backup copies and restoration are controlled separately by
Android and your backup provider; deleting local app storage does not promise
immediate deletion of backups. The developer holds no server copy of your typing
or learned dictionaries to delete.

## Children, changes and contact

The app has no account system and does not operate a developer collection service
for adults or children. The local processing and external services described above
still apply. Changes to data handling will be reflected in this policy with an
updated effective date. Contact **priv@baodeep.com** with privacy questions.
