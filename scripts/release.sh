#!/usr/bin/env bash
# Publishes a new Slip Away release on GitHub. GitHub Actions then builds the signed APK and
# attaches it to the release (see .github/workflows/android.yml and docs/releasing.md).
#
#   scripts/release.sh            patch release: 0.2.0 -> 0.2.1   (fixes)
#   scripts/release.sh minor      minor release: 0.2.1 -> 0.3.0   (new features)
#   scripts/release.sh major      major release: 0.3.0 -> 1.0.0   (big changes)
#   scripts/release.sh 1.2.3      exactly this version
#
# Options:
#   --dry-run    check everything and show what would happen, but publish nothing
#   --yes        don't ask for confirmation
#   --no-wait    don't wait for the APK build to finish
#
# The version comes from the git tag it creates (v1.2.3); nothing in the code needs editing.
# It never commits for you: commit your changes first. It refuses to release from a dirty tree,
# from a branch other than main, or when main is behind GitHub.
set -euo pipefail

cd "$(dirname "$0")/.."

bump="patch"
assume_yes=false
wait_for_build=true
dry_run=false
for arg in "$@"; do
    case "$arg" in
        patch|minor|major) bump="$arg" ;;
        --dry-run|-n) dry_run=true ;;
        --yes|-y) assume_yes=true ;;
        --no-wait) wait_for_build=false ;;
        -h|--help) sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        [0-9]*.[0-9]*.[0-9]*) bump="exact:$arg" ;;
        v[0-9]*.[0-9]*.[0-9]*) bump="exact:${arg#v}" ;;
        *) echo "Unknown argument: $arg (try --help)"; exit 2 ;;
    esac
done

die() { echo "✗ $*" >&2; exit 1; }
say() { echo "• $*"; }
warn() { echo "! $*" >&2; }

command -v gh >/dev/null || die "The GitHub CLI (gh) is needed: https://cli.github.com"
# None of the git/gh calls below may read the keyboard: anything typed early belongs to the
# "Publish?" question, not to them.
gh auth status </dev/null >/dev/null 2>&1 || die "Log in to GitHub first: gh auth login"

repo="$(gh repo view --json nameWithOwner -q .nameWithOwner </dev/null 2>/dev/null)" || die "No GitHub repository for this folder"

# ---- the working tree must be clean, on main, and in sync with GitHub ----
problems=0
check() { if $dry_run; then warn "$*"; problems=$((problems + 1)); else die "$*"; fi; }

branch="$(git rev-parse --abbrev-ref HEAD)"
[ "$branch" = "main" ] || check "You're on '$branch'. Switch to main to release."
if [ -n "$(git status --porcelain)" ]; then
    git status --short
    check "You have uncommitted changes. Commit (or stash) them first; a release only contains committed work."
fi
[ -f .github/workflows/android.yml ] || die ".github/workflows/android.yml is missing; GitHub wouldn't build an APK."

say "Checking GitHub…"
git fetch --quiet --tags origin </dev/null
if git rev-parse -q --verify origin/main >/dev/null; then
    behind="$(git rev-list --count HEAD..origin/main)"
    ahead="$(git rev-list --count origin/main..HEAD)"
else
    behind=0
    ahead="$(git rev-list --count HEAD)" # main isn't on GitHub yet
fi
[ "$behind" -eq 0 ] || check "main is $behind commit(s) behind GitHub. Run: git pull"

secrets="$(gh secret list --repo "$repo" </dev/null 2>/dev/null | cut -f1 || true)"
for s in SLIPAWAY_KEYSTORE_BASE64 SLIPAWAY_STORE_PASSWORD; do
    grep -qx "$s" <<<"$secrets" || check "GitHub secret $s is missing; see docs/releasing.md"
done

# ---- next version ----
last="$(git tag -l 'v[0-9]*.[0-9]*.[0-9]*' --sort=-v:refname | head -1)"
if [ -z "$last" ]; then
    # First release: count on from the fallback version in the build file.
    base="$(sed -n 's/.*?: "\([0-9]*\.[0-9]*\.[0-9]*\)".*/\1/p' app/build.gradle.kts | head -1)"
    last="v${base:-0.0.0}"
    first=true
else
    first=false
fi
IFS=. read -r major minor patch <<<"${last#v}"
case "$bump" in
    patch) next="$major.$minor.$((patch + 1))" ;;
    minor) next="$major.$((minor + 1)).0" ;;
    major) next="$((major + 1)).0.0" ;;
    exact:*) next="${bump#exact:}" ;;
esac
tag="v$next"
git rev-parse -q --verify "refs/tags/$tag" >/dev/null && die "$tag already exists. Pick a higher version."
if ! $first; then
    if [ "$(printf '%s\n%s\n' "${last#v}" "$next" | sort -V | tail -1)" != "$next" ] || [ "$last" = "$tag" ]; then
        die "$tag isn't higher than the last release ($last); phones would refuse it as an update."
    fi
fi
IFS=. read -r nmaj nmin npat <<<"$next"
[ "$nmin" -lt 100 ] && [ "$npat" -lt 100 ] || die "Minor and patch numbers must stay below 100 (they make up the versionCode)."

echo
if $first; then
    echo "  Release:   $tag   (first release; the build file says ${last#v}, $bump)"
else
    echo "  Release:   $tag   (last: $last, $bump)"
fi
echo "  Version:   versionName $next, versionCode $((nmaj * 10000 + nmin * 100 + npat))"
echo "  Commit:    $(git log -1 --format='%h %s')"
[ "$ahead" -gt 0 ] && echo "  Also pushes $ahead local commit(s) to GitHub first."
echo "  Repo:      $repo"
echo

if $dry_run; then
    if [ "$problems" -gt 0 ]; then
        echo "Dry run: fix the $problems problem(s) above, then run it without --dry-run."
    else
        echo "Dry run: everything is ready. Run it without --dry-run to publish $tag."
    fi
    exit 0
fi

if ! $assume_yes; then
    # Read the answer from the keyboard itself (not from anything piped or typed ahead), and
    # accept y / yes in any case, ignoring stray spaces or a carriage return from the terminal.
    answer=""
    if { exec 3</dev/tty; } 2>/dev/null; then
        read -r -u 3 -p "Publish $tag? [y/N] " answer || true
        exec 3<&-
    else
        read -r -p "Publish $tag? [y/N] " answer || true
    fi
    answer="$(printf '%s' "$answer" | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')"
    if [[ "$answer" != "y" && "$answer" != "yes" ]]; then
        echo "Cancelled${answer:+ (you answered '$answer'; type y and press Enter to publish)}."
        exit 0
    fi
fi

# ---- publish ----
if [ "$ahead" -gt 0 ]; then
    say "Pushing main…"
    git push --quiet origin main
fi
say "Creating release $tag…"
url="$(gh release create "$tag" </dev/null --repo "$repo" --target "$(git rev-parse HEAD)" \
    --title "Slip Away $next" --generate-notes)"
echo "  $url"

if ! $wait_for_build; then
    say "GitHub is building the APK now (Actions tab). It will appear on the release in about 5–10 minutes."
    exit 0
fi

# ---- follow the APK build ----
say "Waiting for GitHub to start the APK build…"
run_id=""
for _ in $(seq 1 30); do
    run_id="$(gh run list --repo "$repo" --workflow android.yml --event release --limit 5 \
        --json databaseId,headBranch -q ".[] | select(.headBranch == \"$tag\") | .databaseId" </dev/null | head -1)"
    [ -n "$run_id" ] && break
    sleep 5
done
[ -n "$run_id" ] || die "No build started for $tag. Check the Actions tab: https://github.com/$repo/actions"

say "Building (Ctrl+C stops watching; the build keeps going)…"
if gh run watch "$run_id" --repo "$repo" --exit-status --interval 15 </dev/null >/dev/null; then
    apk="slipaway-$next.apk"
    echo
    echo "✓ $tag is out."
    echo "  APK:     https://github.com/$repo/releases/download/$tag/$apk"
    echo "  Release: $url"
else
    die "The APK build failed. Logs: gh run view $run_id --repo $repo --log-failed"
fi
