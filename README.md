# PulsePing

PulsePing is a lightweight Android network reachability monitor. Choose a public DNS target or enter a hostname or IPv4 address, then probe it every 2, 5, or 10 seconds. The app displays recent response time, average jitter, and session packet loss while its foreground service is active. Use the notification actions to pause, resume, or stop monitoring.

Results are diagnostic only. Android's `InetAddress.isReachable` can use ICMP or a platform fallback, and some networks block these probes. Results depend on the device, network, and destination; a timeout does not always mean the host or internet is offline.

## Build an APK on GitHub

1. Create a GitHub repository and upload the project files, keeping their directory structure.
2. Open the repository's **Actions** tab and allow workflows to run if GitHub asks.
3. Open the latest **Build Android APK** run and wait for it to finish.
4. In that run's **Artifacts** section, download **PulsePing-Release**. Extract the archive to get `app-release.apk`.
5. Transfer the APK to an Android device and install it. You may need to allow installation from the app used to open the APK.

The workflow builds a release APK on each push and pull request, and can also be started manually with **Run workflow**.