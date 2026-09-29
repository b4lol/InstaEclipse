# Security Policy

## Supported versions

Only the latest release receives fixes. Please update before reporting.

| Version | Supported |
|---------|-----------|
| latest release | ✅ |
| older releases | ❌ |

## Reporting a vulnerability

**Please do not open a public issue for security problems.**

Use GitHub's private reporting instead:
[Report a vulnerability](https://github.com/ReSo7200/InstaEclipse/security/advisories/new).
If that isn't available, contact the maintainer privately via the Telegram channel linked in
the README and ask for a private channel.

Include:

- the InstaEclipse version and Instagram version,
- Android version and framework (LSPosed / LSPatch),
- what an attacker needs (another installed app? root? physical access?),
- steps to reproduce or a proof of concept.

You should get an acknowledgement within 7 days. Please give us a reasonable window to ship a
fix before disclosing publicly.

## Threat model

InstaEclipse is two things in one APK:

1. an **Xposed module** that runs *inside Instagram's process*, with Instagram's uid and
   permissions, and
2. a **companion app** (settings UI, SAF downloader) running under its own uid.

They talk over broadcasts and a few exported components. In scope:

- another installed, unprivileged app abusing that bridge (changing settings, reading
  settings or logs, triggering downloads, overwriting Instagram's config);
- data the module stores or exposes (passcode hash, logs, downloaded media, cached stories).

Out of scope:

- attackers with root, ADB or an unlocked bootloader — they can already read Instagram's data;
- Instagram/Meta detecting the module or acting on the account (see [DISCLAIMER.md](DISCLAIMER.md));
- modified Instagram clients that are listed as supported packages.

## Design notes for contributors

- Receivers registered **inside the hooked app** must use
  `IpcSecurity.registerCompanionOnlyReceiver` (sender must hold the signature permission
  `ps.reso.instaeclipse.permission.MODULE_IPC`).
- Broadcasts **from the companion** must be package-targeted
  (`CommonUtils.broadcastToInstagram` or `setPackage`). Never send settings implicitly.
- Request/reply flows **back to the companion** must carry and check a nonce
  (`IpcSecurity.newNonce` / `echoNonce` / `nonceMatches`).
- Every extra received by an exported component is untrusted: validate URLs, file names,
  packages and actions against allowlists (see `DownloadRequestValidator`).
- Never log secrets (passcodes, hashes, session tokens, cookies).

The most recent review is in [docs/SECURITY_AUDIT.md](docs/SECURITY_AUDIT.md).
