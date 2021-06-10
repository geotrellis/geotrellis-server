---
name: Release
about: When ready to cut a release
title: Release X.Y.Z
labels: release
assignees: ''

---

- [ ] Make sure `main` is up-to-date: `git checkout -f main && git pull origin main`
- [ ] Rotate `CHANGELOG.md` (following [Keep a Changelog](https://keepachangelog.com/) principles)
- [ ] Ensure outstanding changes are committed:
```bash
$ git status # Is the git staging area clean?
$ git add CHANGELOG.md
$ git commit -m "X.Y.Z"
```
- [ ] Make and push an annotated tag for your release and push `main`:
```bash
$ git tag -s -a vX.Y.Z -m "Release version <version>"
$ git push origin --tags
$ git push origin main
```
- [ ] Ensure that CI checks pass
