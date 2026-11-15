# Private APK backup automation

The private-backups branch stores long-lived binary copies of signed release APKs and the mobile license issuer APKs. The branch is private with this repository. GitHub Actions writes a versioned directory for each build and includes SHA-256 checksum files.

This is a retention backup inside the same GitHub account, not an absolute guarantee against account loss, deletion, or provider failure. Keep a separate encrypted offline copy of all signing materials.
