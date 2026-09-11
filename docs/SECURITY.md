# Security

## API credentials

The API key survives app restarts but is never plaintext at rest. Encrypt it with an Android Keystore-backed key and store only ciphertext plus required non-secret parameters. Do not put the key in source code, resources, BuildConfig, Room, plaintext DataStore, request snapshots, crash metadata, analytics, or export files.

Base URL and other non-secret preferences may use DataStore. Updating the key or base URL affects only requests submitted afterward.

The initial implementation uses a non-exportable 256-bit AES key in Android Keystore with GCM authentication. DataStore contains only Base64-encoded ciphertext and its per-encryption initialization vector. The settings UI never reads an existing key back into a text field; it exposes only whether a key exists.

## Network and logging

Use HTTPS by default. If a user configures a non-HTTPS endpoint, reject it unless a deliberately documented debug-only policy exists. Never log Authorization headers, request bodies, responses, or full novel content. Release HTTP logging must be disabled or redact both headers and bodies.

DS Writer accepts plain HTTP for one documented production purpose: reaching an OpenAI-compatible model server on the user's own private network, which such servers expose over HTTP without TLS. The restriction is enforced in `BaseUrlValidator`, which accepts `http://` only for private, loopback, and link-local addresses and rejects it for every public host, and Settings shows a Chinese warning while a LAN address is active. At the platform layer `res/xml/network_security_config.xml` permits cleartext in its base configuration, because an Android network security config matches hostnames and cannot express a private address range, and explicitly pins `api.deepseek.com` to `cleartextTrafficPermitted="false"`. A future release should narrow the base configuration to an explicit domain allowlist once the LAN cases are verified on real devices; until then the validator is the authoritative gate and no public endpoint may use cleartext.

Present authentication, throttling, network, and certificate failures in concise Simplified Chinese without exposing secrets or internal stack traces.

Background generation requests only the platform permissions required for its visible foreground service. Notification permission is requested only on Android 13+ from a send or regenerate action. The app does not request battery-optimization exemption, broad storage access, or unrelated background permissions.

Connection tests call authenticated `GET /models`, which avoids sending conversation content or triggering a generation. The shared OkHttp client has no HTTP logging interceptor.

## Data protection

Readable exports and Android backup rules exclude secrets. Temporary images use private storage and are removed when no longer needed. Validate content URIs and use Android Photo Picker rather than broad media access.

Readable export code has no dependency on settings storage or the API-key cipher. Its DTOs have no key, ciphertext, IV, Keystore alias, Authorization field, reasoning, attachment path, or task metadata. The create-document contract performs user-directed file access without storage permission. Exact user/assistant text is not secret-filtered: if a user deliberately wrote credential-like text as a real message, preserving that exact real message is required by export fidelity and the pure-conversation invariant. There is no JSON import or restore parser.

Platform cloud backup and device-to-device transfer remain disabled. `android:allowBackup="false"` is reinforced by Android 12+ data-extraction rules excluding every app storage domain; user-created exports are the only supported transfer path.

Vision selection uses the system Photo Picker and requests no broad photo or storage permission. The selected URI is read only after the user action, validated by decoded content rather than its filename, and copied once into the app-private `files/attachments` directory. Request encoding accepts only canonical files directly inside that directory and verifies the persisted byte count before reading. Removing an unsent image deletes its private copy.

Security tests target encryption abstraction behavior, export exclusion, request snapshot exclusion, absence of JSON restore code, and release logging configuration.

## Release signing

Direct APK releases use a long-lived private signing key stored outside the repository. The Gradle project may read signing coordinates from the release workstation's protected user directory, but no keystore, password, signing property, or private-key material may be committed, packaged as an application resource, printed by build automation, or uploaded as a public artifact. A fork uses its own key. Because clean release rebuilds are not byte-reproducible, the archived versioned APK — not the build output — is the authoritative artifact for a recorded digest.

An offline backup of the signing key and its password file is required; losing both ends the ability to ship compatible upgrades. Keep the archive and the password in separate places, both outside the repository.

This build has no in-app update mechanism. There is no update client, no update origin, and no
`REQUEST_INSTALL_PACKAGES` permission, so the app never hands an APK to the package installer and
never asks the user to authorise an unknown source. Installing a newer version is an ordinary
manual install. The released APK requests only `INTERNET`, `POST_NOTIFICATIONS`, and the two
foreground-service permissions; an instrumentation test asserts that the install permission and
the APK FileProvider are both absent, so the removal cannot silently regress.

The release audit must confirm that production sources contain no platform logger calls, HTTP logging interceptor, stack-trace printing, request/response logging, or analytics/crash content capture. The request inspector implementation lives only in the `debug` source set; the `release` source set supplies a no-op boundary, resource shrinking removes its copy, and it never contains the API key. Inspect the minified release mapping and packaged manifest in addition to running tests; routine verification must not contact a paid model endpoint.
