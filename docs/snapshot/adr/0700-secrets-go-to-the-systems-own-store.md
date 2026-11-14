# ADR-0700: Secrets go to the system's own store

- **Status:** Accepted
- **Date:** 2026-11-14
- **Relates to:** ADR-0291 (which declined the keychain; the owner reopened it,
  D7 (b)), ADR-0683 (the other half of that reopening: opened URLs and one
  running copy)

## Context

A messenger keeps a device-bound refresh token, and today it writes it to an
owner-only file encrypted under a key kept beside it, so anyone who can read
the user's files can take the session (Tessera's G-09, here T-09). Every
desktop has a store made for this: the Secret Service on Linux, the Keychain
on macOS, the Credential Manager on Windows. ADR-0291 left the keychain to the
applications. A second application asking for it is what the owner reopened
it on.

Each store is a library the system already has. The toolkit already speaks
D-Bus through libdbus for notifications, and already binds `user32`,
`shell32` and Core Foundation's neighbours at run time. So nothing has to be
built or bundled.

## Decision

`dev.goldberry.keychain.Keychain`, a sealed interface with `store`, `load`
and `delete` by service and account, over bytes and over UTF-8 text.
`Keychain.system()` is the platform's store; `Keychain.inMemory()` stands in
for it in a test. A refusal is a `KeychainException` with a reason:
`UNAVAILABLE` (no store), `DENIED` (the user dismissed or ignored the unlock
prompt) or `FAILED`.

Under it, in `:natives`, `desktop.keychain.SecretStore` and three stores,
each bound at run time:

- **Linux: the Secret Service over D-Bus,** with no libsecret. Items go in
  the default collection with the attributes `service` and `username`, which
  `secret-tool` and other libraries' keyrings use, and libsecret's generic
  schema attribute, which is written and not searched for. A locked keyring
  is unlocked through the service's prompt, and the call waits for its
  `Completed` signal for up to two minutes, then dismisses it. The session is
  `plain`: the secret crosses the user's own session bus as it is, and anything
  that could read it there can read the unlocked keyring anyway.
- **macOS: `Security.framework`'s `SecItem` calls,** a generic password per
  service and account in the login keychain, with Core Foundation for the
  dictionaries.
- **Windows: `advapi32`'s `CredWriteW`, `CredReadW` and `CredDeleteW`,** a
  generic credential `service:account` whose user name is the account, kept
  on this machine. At most 2560 bytes, which is the Credential Manager's
  limit.

The D-Bus binding moved out of the notifier's package into
`natives.desktop.dbus`, which is not exported. It gained object paths, byte
arrays, structs and variants, a reader that turns any message into plain
values, error replies by name, waiting for one signal, and the serving side
a fake service needs.

The macOS and Windows stores are **UNVERIFIED**: written against the
documentation, marked so in the code, and run by no lane here.

## Consequences

- Every call blocks and may ask the user, so the guide says never on the UI
  thread. Nothing here enforces it.
- On Windows, why a read failed is in `GetLastError`, which a downcall can
  read only through a captured call state that the native image would need
  declared separately. So a failed read is answered as no secret and a
  failed delete as nothing deleted. Only a failed write is an error.
- The stores' downcall shapes are constants on `MacKeychain` and
  `WindowsCredentials`, which `ForeignSurface` initialises with the other
  system-library classes, so the native image's metadata has them wherever
  it is built. The D-Bus additions reuse shapes it already had.
- Tested by `SecretServiceTest` against a fake Secret Service on a private
  `dbus-daemon`: store and load, replace, an item another library stored,
  delete, sessions closed, and a locked keyring whose prompt is accepted,
  dismissed or never answered. `DbusTest` covers the value reader and writer
  and error replies, `SecretStoreReasonsTest` the macOS status mapping and the
  Windows structure's offsets, and `KeychainTest` the core API. No test
  touches the keyring of whoever runs the suite.
