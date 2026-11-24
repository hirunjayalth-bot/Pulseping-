# PulsePing

PulsePing LK is a dark-themed Android gaming network and sensitivity suite. Its PINGER tab probes a selected host every 2, 5, or 10 seconds and displays response time, jitter, and session packet loss. The foreground monitor can also be paused, resumed, or stopped from its low-importance notification or the PulsePing Quick Settings tile.

The SENSI tab turns three upward touch swipes and a device profile into adjustable starting values; these are estimates, not guaranteed game settings. AIM & CROSS includes an optional, non-touchable overlay that requires Android's display-over-other-apps permission and a 15-second reflex trainer. TOOLS includes copyable nicknames and a pre-game checklist. The PINGER tab is ad-free; other tabs use Google test ads and a 45-second interstitial cooldown. Replace the test AdMob IDs before publishing and provide any required privacy disclosures and consent flow.

Results are diagnostic only. Android's `InetAddress.isReachable` can use ICMP or a platform fallback, and some networks block these probes. Results depend on the device, network, and destination; a timeout does not always mean the host or internet is offline.

## Build an APK on GitHub

1. Create a GitHub repository and upload the project files, keeping their directory structure.
2. Open the repository's **Actions** tab and allow workflows to run if GitHub asks.
3. Open the latest **Build Android APK and AAB** run and wait for it to finish.
4. In that run's **Artifacts** section, download **PulsePing-Release**. Extract the archive to get `app-release.apk` and `app-release.aab`.
5. Transfer the APK to an Android device and install it. You may need to allow installation from the app used to open the APK.

The workflow builds a release APK and Android App Bundle on each push and pull request, and can also be started manually with **Run workflow**. CI generates a temporary signing key so builds are reproducible; configure a private, persistent Play App Signing upload key for production releases instead.