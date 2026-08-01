# Hunt.it Play Store release

## Permanent identity

- Application ID: `com.ghostdev.huntit`
- Upload-key alias: `huntit-upload`
- Upload keystore: `~/.android/huntit-upload.jks`
- Keychain service: `huntit-upload-keystore`
- Keychain account: `com.ghostdev.huntit`

The upload keystore and its password are intentionally stored outside the
repository. Never copy either value into a tracked file.

## Build the Play bundle

On the Mac where the upload key was created:

```bash
./scripts/build-play-bundle.sh
```

The verified bundle is written to:

```text
composeApp/build/outputs/bundle/release/composeApp-release.aab
```

The build fails rather than producing an unsigned release when the required
signing environment is unavailable.

## Version every upload

Update both values in `gradle.properties` before creating a new Play release:

```properties
huntitVersionCode=2
huntitVersionName=1.0.1
```

`huntitVersionCode` must increase for every artifact uploaded to Play Console.

## Upload certificate

- SHA-1: `EE:05:FC:77:9B:40:EB:C2:D3:7D:AA:45:8C:6F:F3:80:0C:F6:0C:46`
- SHA-256: `87:EC:FA:F6:0A:45:08:FA:90:85:1E:81:90:70:2D:D6:66:A4:DA:A7:9E:91:B6:E3:4B:EF:55:29:8A:EC:FE:8C`

For the first Play Console release, enroll in Play App Signing and use this
certificate as the upload certificate. Google Play should manage the separate
app-signing key used for distribution.

## Required key backup

Before the first upload:

1. Copy `~/.android/huntit-upload.jks` to two secure, encrypted backup
   locations.
2. Open macOS Keychain Access and securely record the password for service
   `huntit-upload-keystore` and account `com.ghostdev.huntit` in the chosen
   password manager.
3. Confirm that one backup can be restored before depending on it.

Losing the upload key requires an upload-key reset through Play Console. Do not
share the keystore or its password in chat, source control, email, or issue
trackers.
