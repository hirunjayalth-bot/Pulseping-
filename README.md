# PulsePing LK

PulsePing LK is a lightweight Android reachability monitor. It probes a selected host on a 2, 5, or 10 second interval while its foreground service is active, and displays recent response time, average jitter, and session packet loss. Use the notification actions to pause, resume, or stop monitoring.

The app reports diagnostic reachability only. Android's `InetAddress.isReachable` can use ICMP or a platform fallback, so results depend on the device, network, and destination. It does not change or optimize game traffic, and a failed probe does not necessarily mean a game server is unavailable.

## Build an APK on GitHub

1. Create a GitHub repository and upload the project files, keeping their directory structure.
2. Open the repository's **Actions** tab and allow workflows to run if GitHub asks.
3. Open the latest **Build Android APK** run and wait for it to finish.
4. In that run's **Artifacts** section, download **app-debug**. Extract the archive to get `app-debug.apk`.
5. Transfer the APK to an Android device and install it. You may need to allow installation from the app used to open the APK.

The workflow builds the debug APK on each push and pull request, and can also be started manually with **Run workflow**.