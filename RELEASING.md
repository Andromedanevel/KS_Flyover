# Releasing Flyover

Versions follow `MAJOR.MINOR.PATCH`:

- **PATCH** (1.0.0 → 1.0.1): a bug fix, nothing new to set.
- **MINOR** (1.0.0 → 1.1.0): a new feature or setting. Existing settings keep working.
- **MAJOR** (1.x → 2.0.0): something that breaks existing settings or needs a much newer Kiosk Satellite.

Every release must be higher than the last one, or Kiosk Satellite won't offer it as an update. Never reuse or delete a published version.

## Checklist

1. Make the change and run the tests:
   ```
   python3 tools/test.py
   ```
2. Set the new version in **two places**, the same number in both:
   - `"version"` in `kiosk-satellite-plugin.json`
   - a new heading at the top of `CHANGELOG.md`, with what changed
3. If the change uses something from a newer Kiosk Satellite, raise `"minAppVersion"` too.
4. Commit and push:
   ```
   git commit -am "1.0.1: what changed"
   git push
   ```
5. On GitHub: **Releases → Draft a new release**
   - **Tag:** `v` + the version, e.g. `v1.0.1` (choose "Create new tag")
   - **Title:** the version, e.g. `1.0.1`
   - **Description:** paste the CHANGELOG entry
   - Leave **Set as a pre-release** off, then **Publish release**
6. Wait for the green tick in **Actions**. The release then has three files: `flyover-1.0.1.zip`, `flyover-1.0.1.zip.sha256` and `kiosk-satellite-plugin.json`. Never upload or replace these by hand.
7. Update the kiosks: **Plugin Manager → Flyover → check for updates**, or for a ZIP-installed copy **Developer Tools → Install from ZIP** with the new ZIP.

Once listed in Community Plugins, nothing else is needed: the directory picks up the latest stable release twice a day.

## Listing in Community Plugins (once)

1. Replace `Andromedanevel` in `README.md` with your GitHub username, commit, and release.
2. Run the same check the submission runs:
   ```
   python3 tools/community_plugins.py check https://github.com/Andromedanevel/ks-flyover
   ```
3. Open a **Submit a plugin** issue at https://github.com/jxlarrea/kiosk-satellite-plugins/issues/new?template=submit-plugin.yml from the account that owns the repository, and paste the repository URL.
4. The check posts its result on the issue. A maintainer reviews the code; once approved the plugin appears within 12 hours. Keep the plugin ID `flyover` from then on.
