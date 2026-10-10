# Puts Monologue's existing release signing key into GitHub Actions secrets so CI can build APKs that update
# installed copies. Every release so far is signed with this PC's Android debug keystore, and the Google sign-in
# OAuth client is registered for that key, so the key must stay the same.
#
# Run once on the PC that builds releases, from the repository folder:
#   powershell -ExecutionPolicy Bypass -File scripts\setup-release-signing.ps1
#
# Needs GitHub CLI logged in (`gh auth login`) and a JDK keytool.
# BACK UP %USERPROFILE%\.android\debug.keystore: if it is lost, installed copies can no longer update.
$ErrorActionPreference = 'Stop'
$repo = 'HKmario852/monologue'
$keystore = Join-Path $HOME '.android\debug.keystore'
# SHA-256 of the certificate that signs every published release (also checked by CI).
$expected = '0CCFB3F2D2245BB084C2339997FA96110A0574EB7B295235126CC10C6C21CE19'

if (-not (Test-Path $keystore)) { throw "No keystore at $keystore." }
$keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source
if (-not $keytool) {
    $keytool = @("$env:JAVA_HOME\bin\keytool.exe", "$env:ProgramFiles\Android\Android Studio\jbr\bin\keytool.exe") |
        Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
}
if (-not $keytool) { throw 'keytool not found. Set JAVA_HOME or install Android Studio, then run again.' }
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'GitHub CLI (gh) not found: https://cli.github.com' }

# Refuse a different key (for example another PC's debug keystore): it could never update installed copies.
$listing = & $keytool -list -v -keystore $keystore -storepass android -alias androiddebugkey 2>$null | Out-String
$actual = ([regex]::Match($listing, 'SHA256:\s*([0-9A-F:]{95})')).Groups[1].Value -replace ':', ''
if ($actual -ne $expected) { throw "This keystore's certificate ($actual) is not the release key ($expected). Not uploading." }

gh secret set MONOLOGUE_KEYSTORE_BASE64 -R $repo --body ([Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore)))
gh secret set MONOLOGUE_KEYSTORE_PASSWORD -R $repo --body 'android'
gh secret set MONOLOGUE_KEY_ALIAS -R $repo --body 'androiddebugkey'

Write-Host 'Done: CI now signs release builds with the same key as published releases.'
Write-Host "Back up $keystore (password manager or USB). Never commit it."
