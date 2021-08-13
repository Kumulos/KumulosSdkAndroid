### Description of Changes

(briefly outline the reason for changes, and describe what's been done)

### Breaking Changes

- None

### Release Checklist

Prepare:

- [ ] Detail any breaking changes. Breaking changes require a new major version number
- [ ] Check `./gradlew assemble` passes w/ no errors

Bump versions in:

- [ ] README.md

Release:

- [ ] Squash and merge to master
- [ ] Delete branch once merged
- [ ] Create tag from master matching chosen version
- [ ] Verify & Publish release from [OSS Nexus UI](https://oss.sonatype.org/#stagingRepositories)

Post Release:

Update docs site with correct version number references

- [ ] https://docs.kumulos.com/developer-guide/sdk-reference/android/
- [ ] https://docs.kumulos.com/getting-started/integrate-app/

Update changelog:

- [ ] https://docs.kumulos.com/developer-guide/sdk-reference/android/#changelog
