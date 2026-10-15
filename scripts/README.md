# opencode Merge Conflict Resolution Scripts

This directory contains helper scripts for resolving Git merge conflicts using opencode with iterative verification.

## opencode-resolve-merge

A script that uses opencode (or a configured LLM API) to resolve Git merge conflicts with iterative verification.

### Features

- **Automatic Conflict Detection**: Identifies files with merge conflict markers
- **LLM-Powered Resolution**: Uses opencode to generate intelligent merges
- **Iterative Verification**: Runs build commands to verify resolutions compile
- **Multiple Attempts**: Retries up to N times if verification fails
- **Fallback Strategies**: Falls back to simpler strategies if LLM is unavailable

### Usage

```bash
./scripts/opencode-resolve-merge \
  --file <path/to/conflicted/file> \
  --base <base-branch> \
  --head <head-branch> \
  --verify-command "./gradlew :composeApp:compileAndroidMain" \
  --max-iterations 5 \
  --workdir /tmp/opencode-workdir \
  --output /tmp/resolved-file
```

### Environment Variables

The script uses the following environment variables:

- `OPECODE_API_KEY`: API key for opencode/LLM access
- `OPECODE_MODEL`: Model identifier to use
- `OPECODE_ENDPOINT`: API endpoint URL

### Integration with GitHub Actions / Forgejo Workflows

This script is designed to be used in the `.forgejo/workflows/auto-merge.yml` workflow.
The workflow:

1. Detects merge conflicts after attempting to merge upstream
2. For each conflicted file, calls this script
3. The script uses opencode to resolve the conflict
4. Verifies the resolution compiles with gradle
5. If verification fails, retries up to 3 times
6. Files that can't be resolved automatically create a PR for manual review

### Requirements

- Git
- curl (for API calls)
- jq (for JSON parsing)
- gradle (for verification)
- opencode CLI or configured LLM API endpoint

### Merge Strategy

The script follows these rules when resolving conflicts:

1. **Preserve Custom Code**: Always prioritize custom features from the fork (OURS)
2. **Integrate Upstream Fixes**: Adopt bug fixes, dependency updates, and new features from upstream (THEIRS)
3. **No Breaking Changes**: Never remove classes, functions, or variables that exist in the fork
4. **Safe Fallback**: If uncertain, preserve the fork's version

### Error Handling

- If the LLM API is unavailable, falls back to git's merge-file or simple strategies
- If verification fails repeatedly, marks the file as needing manual resolution
- Creates a PR with all unresolved conflicts for manual review

### Customization

To customize the merge behavior:

1. Edit the prompt in `opencode-resolve-merge` to change the LLM's instructions
2. Adjust `MAX_ATTEMPTS` in the workflow to change retry behavior
3. Modify the `VERIFY_COMMAND` to use different build targets

### Testing

You can test the script locally:

```bash
# Create a test conflict
git checkout -b test-merge
# Make conflicting changes
echo "conflict" > test.txt
git add test.txt
git commit -m "test"

# Try to merge with a conflicting branch
git checkout main
echo "different" > test.txt  
git add test.txt
git commit -m "main"
git checkout test-merge
git merge main --no-edit || true

# Resolve with opencode
./scripts/opencode-resolve-merge \
  --file test.txt \
  --base main \
  --head test-merge \
  --verify-command "echo 'Build successful'" \
  --max-iterations 3 \
  --workdir /tmp/test-opencode \
  --output /tmp/resolved-test.txt
```
