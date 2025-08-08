#!/bin/bash

set -e

# EXAMPLES
# ./infra/squash-against-master.sh "feat: add new feature" (uses default branch)
# ./infra/squash-against-master.sh "fix: resolve bug" "main" (squashes against "main")



# Default target branch
DEFAULT_BRANCH="develop"

# Conventional Commit pattern regular expression
CC_REGEX="^(feat|fix|chore|docs|style|refactor|perf|test|build|ci|revert)(\([\w\-]+\))?: .{1,}$"

# Check message argument
if [ -z "$1" ]; then
  echo "❌ Usage: $0 \"<conventional commit message>\" [target_branch]"
  echo "   Default target branch: $DEFAULT_BRANCH"
  exit 1
fi

COMMIT_MSG="$1"
TARGET_BRANCH="${2:-$DEFAULT_BRANCH}"

# Validate against Conventional Commit pattern
if ! [[ "$COMMIT_MSG" =~ $CC_REGEX ]]; then
  echo "❌ Commit message does not follow Conventional Commits format:"
  echo "   ➤ Valid format example: \"feat(login): add login screen\""
  echo "   ➤ Your message: \"$COMMIT_MSG\""
  exit 1
fi

# Check current branch
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$CURRENT_BRANCH" = "HEAD" ]; then
  echo "❌ You are in a detached HEAD state."
  exit 1
fi

# Fetch target branch
echo "🔄 Fetching origin/$TARGET_BRANCH..."
git fetch origin "$TARGET_BRANCH"

# Find merge-base
MERGE_BASE=$(git merge-base "origin/$TARGET_BRANCH" HEAD)

# Check if there are commits to squash
COMMITS_TO_SQUASH=$(git rev-list --count ${MERGE_BASE}..HEAD)
if [ "$COMMITS_TO_SQUASH" -eq "0" ]; then
  echo "✅ No commits to squash. Branch is already aligned with $TARGET_BRANCH."
  exit 0
fi

echo "🔍 Found $COMMITS_TO_SQUASH commits to squash into one."

# Do soft reset and squash
echo "🚧 Rewriting history..."
git reset --soft "$MERGE_BASE"
git commit -m "$COMMIT_MSG"

echo "✅ Done. Your branch '$CURRENT_BRANCH' is now squashed on top of origin/$TARGET_BRANCH with message:"
echo
echo "   \"$COMMIT_MSG\""
echo
echo "👉 Next step: git push --force && open PR."
