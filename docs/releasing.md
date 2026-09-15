# Releasing Camel Kit

Release `0.4.0` is followed by development version `0.4.1-SNAPSHOT`.
Release tags contain fixed POM versions; `main` contains the next snapshot version.

## Prepare

1. Start from reviewed `main` in a clean release branch. Update README, changelog,
   documentation and the companion website. Keep Ship labeled Technology Preview.
2. Set the reactor version to `0.4.0` and the root SCM tag to `camel-kit-0.4.0`.
   Pin `knowledge.mcp.version` in `distribution.properties` to the published Knowledge release.
   Synchronize both JBang launcher fallbacks using the existing release goal:

   ```bash
   ./mvnw -B -N antrun:run@sync-jbang-launcher-versions
   ```
3. Run the full release build with artifact signing (a local GPG key is required):

   ```bash
   ./mvnw -B -Prelease,sourcecheck,linux-ship-certification clean install
   ```

4. Review the diff, commit with `git commit -S`, and tag that exact commit as `camel-kit-0.4.0`.
   Record its full SHA. Keep the generated release artifacts for inspection.
5. Advance every reactor POM to `0.4.1-SNAPSHOT` and restore the root SCM tag to `HEAD`.
   Run the launcher synchronization goal again; keep Knowledge pinned to its fixed release.
   Validate the development build, sign the next-development commit, and open a PR.
   Merge the reviewed PR and push the prepared tag before dispatching publication.
   The release workflow is manual: pushing a branch or tag does not publish artifacts.

## Publish the prepared tag

The repository needs these GitHub Actions secrets:

- `MVN_CENTRAL_USER` and `MVN_CENTRAL_PASSWORD`: Central Portal token credentials.
- `GPG_ID`: artifact-signing key ID.
- `GPG_KEY`: base64-encoded private signing key, using the existing empty-passphrase convention.

Run **Publish Maven Release** (`release.yml`) from `main`, with the prepared tag and its
full reviewed commit SHA in `expected_sha`. It checks the tag and all reactor versions,
then runs the full build, signs artifacts and waits for Central publication. It does
not create commits, move tags, merge branches, or create a GitHub release.

Publish Knowledge first, then verify its `runner` artifact is available before publishing Camel Kit.
After publication, verify the tag-pinned JBang install and Camel JBang plugin from a clean cache.
Create the GitHub release with reviewed changelog notes, `--verify-tag` and `--latest`.
The unqualified JBang alias continues to follow the development branch.
