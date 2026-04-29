#!/usr/bin/env bash
set -euo pipefail

usage()
{
    echo "usage: scripts/release-version.sh <patch|minor|major>" >&2
}

if [ "$#" -ne 1 ]; then
    usage
    exit 2
fi

bump="$1"
case "$bump" in
    patch|minor|major) ;;
    *)
        usage
        exit 2
        ;;
esac

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

require_clean_worktree()
{
    local status
    status="$(git status --porcelain)"
    if [ -n "$status" ]; then
        echo "Refusing to release because the working tree is not clean." >&2
        echo >&2
        echo "$status" >&2
        exit 1
    fi
}

set_project_version()
{
    local version="$1"
    python3 - "$version" <<'PY'
import re
import sys
from pathlib import Path

version = sys.argv[1]
pom = Path("pom.xml")
text = pom.read_text()
updated, count = re.subn(r"(<version>)[^<]+(</version>)", rf"\g<1>{version}\2", text, count=1)
if count != 1:
    raise SystemExit("Could not update the project version in pom.xml")
pom.write_text(updated)
PY
}

step()
{
    local number="$1"
    local name="$2"
    printf '\n[%s] %s\n\n' "$number" "$name"
}

require_synced_with_upstream()
{
    local ahead behind
    read -r ahead behind < <(git rev-list --left-right --count HEAD..."$upstream")
    if [ "$ahead" -ne 0 ] || [ "$behind" -ne 0 ]; then
        echo "Refusing to release because the current branch is not in sync with $upstream." >&2
        echo "Ahead:  $ahead" >&2
        echo "Behind: $behind" >&2
        exit 1
    fi
}

require_clean_worktree

upstream="$(git rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>/dev/null || true)"
if [ -z "$upstream" ]; then
    echo "Refusing to release because the current branch has no upstream tracking branch." >&2
    exit 1
fi

step "1" "Update local branch from $upstream"
git pull --ff-only
require_clean_worktree
require_synced_with_upstream

current_version="$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' pom.xml | head -n 1)"
if [[ ! "$current_version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)-SNAPSHOT$ ]]; then
    echo "Expected pom.xml to contain a x.y.z-SNAPSHOT project version, found: $current_version" >&2
    exit 1
fi

major="${BASH_REMATCH[1]}"
minor="${BASH_REMATCH[2]}"
patch="${BASH_REMATCH[3]}"

release_version="$major.$minor.$patch"
case "$bump" in
    patch)
        next_version="$major.$minor.$((patch + 1))-SNAPSHOT"
        ;;
    minor)
        next_version="$major.$((minor + 1)).0-SNAPSHOT"
        ;;
    major)
        next_version="$((major + 1)).0.0-SNAPSHOT"
        ;;
esac
tag="v$release_version"

if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
    echo "Refusing to release because local tag already exists: $tag" >&2
    exit 1
fi
if git ls-remote --exit-code --tags origin "refs/tags/$tag" >/dev/null 2>&1; then
    echo "Refusing to release because remote tag already exists: $tag" >&2
    exit 1
fi

cat <<EOF
Current version: $current_version
Release version: $release_version
Next version:    $next_version
Tag:             $tag

This will:
  1. Update pom.xml to $release_version
  2. Run mvn -q spotless:check
  3. Run mvn clean test
  4. Commit: Release $tag
  5. Push the release commit
  6. Create annotated tag: $tag
  7. Push the tag
  8. Update pom.xml to $next_version
  9. Commit: Prepare for $next_version
 10. Push the next development commit
EOF

read -r -p "Continue? [y/N] " answer
case "$answer" in
    y|Y|yes|YES) ;;
    *)
        echo "Aborted."
        exit 1
        ;;
esac

step "2" "Set release version $release_version"
set_project_version "$release_version"

step "3" "Run Spotless check"
mvn -q spotless:check

step "4" "Run clean test"
mvn clean test

step "5" "Commit release $tag"
git add pom.xml
git commit -m "Release $tag"

step "6" "Push release commit"
git push

step "7" "Create annotated tag $tag"
git tag -a "$tag" -m "Release $tag"

step "8" "Push tag $tag"
git push origin "$tag"

step "9" "Set next development version $next_version"
set_project_version "$next_version"

step "10" "Run Spotless check"
mvn -q spotless:check

step "11" "Commit next development version"
git add pom.xml
git commit -m "Prepare for $next_version"

step "12" "Push next development commit"
git push

printf '\n'
echo "Release $tag complete. Next development version is $next_version."
