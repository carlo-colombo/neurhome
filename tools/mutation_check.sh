#!/usr/bin/env bash
#
# TEST-26 surgical mutation probe.
#
# Applies ONE small production mutation from the list below, runs only the unit tests
# that claim to guard that behaviour, prints which of them failed, then reverts the
# mutation with `git checkout`. It never commits and never edits a test.
#
# Usage:
#   MUTATION=<id> tools/mutation_check.sh        # apply, run, revert one mutation
#   tools/mutation_check.sh --list               # print the mutation list and catchers
#
# A mutation that fails to compile is not coverage: the script reports a build error and
# says so instead of counting it as "caught". Targets under app/src/androidTest cannot be
# run here (no device is allowed in this environment); the `appitem-*` entries are listed
# for completeness and are expected to report "unverified" from a compile-only run.
#
# Limits, stated plainly:
#   * The replacement strings are exact and must occur exactly once. A refactor that
#     changes whitespace breaks the mutation rather than the behaviour under test.
#   * The mutation list only covers behaviours a test name claims to protect. A behaviour
#     with no test at all never appears here, so a "no survivors" result does not mean the
#     suite is complete.
#   * Mutants are single-edit; equivalence between mutants is not explored, so a test that
#     catches one edit may still miss another way of breaking the same behaviour.

set -uo pipefail

cd "$(dirname "$0")/.."
REPO_ROOT="$(git rev-parse --show-toplevel)"

# Tests that claim to guard each mutation's behaviour. Run with --tests.
catchers_for() {
  case "$1" in
    logretention-cutoff|logretention-leapday|logretention-vacuum-threshold|\
    logretention-vacuum-boundary|logretention-daily-gate)
      echo "ovh.litapp.neurhome3.data.repositories.LogRetentionTest ovh.litapp.neurhome3.data.repositories.LogRetentionAnalyzeTest" ;;
    logretention-analyze-gate)
      echo "ovh.litapp.neurhome3.data.repositories.LogRetentionAnalyzeTest" ;;
    cache-score-limit-key|cache-mostused-launch-key)
      echo "ovh.litapp.neurhome3.data.repositories.CachedHomeAppRankingSourceTest" ;;
    resolver-invalidate-noop)
      echo "ovh.litapp.neurhome3.data.repositories.CachedHomeAppResolverTest" ;;
    coordinator-inflight-guard|coordinator-interval|coordinator-debounce)
      echo "ovh.litapp.neurhome3.data.repositories.HomeRankingCoordinatorTest" ;;
    weather-parameter-name)
      echo "ovh.litapp.neurhome3.data.weather.WeatherForecastUrlTest" ;;
    dbvalidator-lower-version-bound|dbvalidator-integrity-check)
      echo "ovh.litapp.neurhome3.application.DatabaseCandidateValidatorTest" ;;
    backup-device-transfer-guard|backup-cse-flip)
      echo "ovh.litapp.neurhome3.backup.DatabaseBackupPayloadTest" ;;
    tagdao-order-by|tagrepo-single-step-clamp|ranking-overfetch)
      echo "ovh.litapp.neurhome3.data.dao.TagDaoTest ovh.litapp.neurhome3.data.repositories.TagRepositoryTest ovh.litapp.neurhome3.data.repositories.HomeAppRankingTest" ;;
    dbvalidator-empty-file-guard)
      echo "ovh.litapp.neurhome3.application.DatabaseCandidateValidatorTest" ;;
    contacts-null-guard)
      echo "ovh.litapp.neurhome3.data.dao.ContactsDaoTest" ;;
    settings-grant-gate)
      echo "ovh.litapp.neurhome3.data.repositories.SettingsRepositoryTest" ;;
    appitem-snapshot-guard)
      echo "" ;;  # androidTest only
    *)
      return 1 ;;
  esac
}

apply_mutation() {
  python3 - "$MUTATION" <<'PY'
import sys

MUTATIONS = {
    # id: list of (path, exact_old_text, new_text)
    "logretention-cutoff": [(
        "app/src/main/java/ovh/litapp/neurhome3/data/repositories/LogRetention.kt",
        "now.minusYears(LOG_RETENTION_YEARS)",
        "now.minusYears(LOG_RETENTION_YEARS + 1)",
    )],
    "logretention-leapday": [(
        "app/src/main/java/ovh/litapp/neurhome3/data/repositories/LogRetention.kt",
        "now.minusYears(LOG_RETENTION_YEARS)",
        "now.minusDays(LOG_RETENTION_YEARS * 365)",
    )],
    "logretention-vacuum-threshold": [(
        "app/src/main/java/ovh/litapp/neurhome3/data/repositories/LogRetention.kt",
        "const val VACUUM_FREELIST_PERCENT = 25L",
        "const val VACUUM_FREELIST_PERCENT = 30L",
    )],
    "logretention-vacuum-boundary": [(
        "app/src/main/java/ovh/litapp/neurhome3/data/repositories/LogRetention.kt",
        "pageCount > 0 && freelistCount * 100 >= pageCount * VACUUM_FREELIST_PERCENT",
        "pageCount > 0 && freelistCount * 100 > pageCount * VACUUM_FREELIST_PERCENT",
    )],
    "logretention-daily-gate": [(
        "app/src/main/java/ovh/litapp/neurhome3/data/repositories/LogRetention.kt",
        'if (settingDao.getOnce(LOG_RETENTION_LAST_RUN_SETTING)?.value != today) {',
        'if (true) {',
    )],
    "logretention-analyze-gate": [(
        "app/src/main/java/ovh/litapp/neurhome3/data/repositories/LogRetention.kt",
        "        if (settingDao.getOnce(DATABASE_ANALYZE_LAST_VERSION_SETTING)?.value == version) return\n",
        "",
    )],
    "cache-score-limit-key": [(
        "app/src/main/java/ovh/litapp/neurhome3/data/repositories/HomeAppRanking.kt",
        "            limit = limit,",
        "            limit = 0,",
    )],
    "cache-mostused-launch-key": [(
        "app/src/main/java/ovh/litapp/neurhome3/data/repositories/HomeAppRanking.kt",
        "        val key = MostUsedKey(launchLogVersion = launchLogVersion(), day = now().toLocalDate())",
        "        val key = MostUsedKey(launchLogVersion = 0, day = now().toLocalDate())",
    )],
    "resolver-invalidate-noop": [(
        "app/src/main/java/ovh/litapp/neurhome3/data/repositories/HomeAppRanking.kt",
        "    fun invalidatePackage(packageName: String, profile: Int) {\n        rows.invalidatePackage(packageName, profile)\n    }",
        "    fun invalidatePackage(packageName: String, profile: Int) = Unit",
    )],
    "coordinator-inflight-guard": [(
        "app/src/main/java/ovh/litapp/neurhome3/data/repositories/HomeRankingCoordinator.kt",
        """        } else if (modelRefreshJob?.isActive == true) {
            // Never restart an in-flight training from a debounced refresh: the running one
            // already reads the newest launches, and restarting would re-read the whole log.
            return
        }
""",
        "        }\n",
    )],
    "coordinator-interval": [(
        "app/src/main/java/ovh/litapp/neurhome3/data/repositories/HomeRankingCoordinator.kt",
        "private const val MODEL_REFRESH_MIN_INTERVAL_MS = 15 * 60 * 1000L",
        "private const val MODEL_REFRESH_MIN_INTERVAL_MS = 0L",
    )],
    "coordinator-debounce": [(
        "app/src/main/java/ovh/litapp/neurhome3/data/repositories/HomeRankingCoordinator.kt",
        "private const val MODEL_REFRESH_DEBOUNCE_MS = 2_000L",
        "private const val MODEL_REFRESH_DEBOUNCE_MS = 30_000L",
    )],
    "weather-parameter-name": [(
        "app/src/main/java/ovh/litapp/neurhome3/data/weather/WeatherForecastUrl.kt",
        'const val CURRENT_FIELDS = "temperature_2m,weather_code"',
        'const val CURRENT_FIELDS = "temperature_2m,weathercode"',
    )],
    "dbvalidator-lower-version-bound": [(
        "app/src/main/java/ovh/litapp/neurhome3/application/DatabaseReplacement.kt",
        "if (schemaVersion < MIN_SCHEMA_VERSION || schemaVersion > maxSchemaVersion) {",
        "if (schemaVersion > maxSchemaVersion) {",
    )],
    "dbvalidator-integrity-check": [(
        "app/src/main/java/ovh/litapp/neurhome3/application/DatabaseReplacement.kt",
        """            if (!quickCheckPasses(db)) {
                return DatabaseCandidateValidation.NotADatabase
            }
""",
        "",
    )],
    "backup-device-transfer-guard": [(
        "app/src/main/java/ovh/litapp/neurhome3/backup/DatabaseBackupPayload.kt",
        "return clientSideEncryptionEnabled && !deviceToDeviceTransfer",
        "return clientSideEncryptionEnabled",
    )],
    "backup-cse-flip": [(
        "app/src/main/java/ovh/litapp/neurhome3/backup/DatabaseBackupPayload.kt",
        "        transportFlags and BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED != 0",
        "        transportFlags and BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED == 0",
    )],
    "tagdao-order-by": [(
        "app/src/main/java/ovh/litapp/neurhome3/data/dao/TagDao.kt",
        'SELECT * FROM Tag ORDER BY position, name COLLATE NOCASE',
        'SELECT * FROM Tag ORDER BY position',
    )],
    "tagrepo-single-step-clamp": [(
        "app/src/main/java/ovh/litapp/neurhome3/data/repositories/TagRepository.kt",
        "val targetIndex = currentIndex + direction.coerceIn(-1, 1)",
        "val targetIndex = currentIndex + direction",
    )],
    "contacts-null-guard": [(
        "app/src/main/java/ovh/litapp/neurhome3/data/dao/ContactsDAO.kt",
        "if (displayName.isNullOrBlank() || phoneNumber.isNullOrBlank()) continue",
        "if (phoneNumber.isNullOrBlank()) continue",
    )],
    "settings-grant-gate": [(
        "app/src/main/java/ovh/litapp/neurhome3/data/repositories/SettingsRepository.kt",
        "        if (enabled && !permissionGranted) return\n",
        "",
    )],
    "appitem-snapshot-guard": [(
        "app/src/main/java/ovh/litapp/neurhome3/ui/components/ApplicationItem.kt",
        """                if (open && editedTags != persistedTags) {
                    tagActions.setTags(app, editedTags)
                    persistedTags = editedTags
                }""",
        """                if (open && editedTags != app.tags.toSet()) {
                    tagActions.setTags(app, editedTags)
                }""",
    )],
    "ranking-overfetch": [(
        "app/src/main/java/ovh/litapp/neurhome3/data/repositories/HomeAppRanking.kt",
        "internal fun homeCandidateLimit(n: Int): Int = n * 8 + 16",
        "internal fun homeCandidateLimit(n: Int): Int = n",
    )],
    "dbvalidator-empty-file-guard": [(
        "app/src/main/java/ovh/litapp/neurhome3/application/DatabaseReplacement.kt",
        "if (!candidate.isFile || candidate.length() == 0L) {",
        "if (!candidate.isFile) {",
    )],
}

mutation_id = sys.argv[1]
if mutation_id not in MUTATIONS:
    sys.exit(f"unknown mutation: {mutation_id}")

for path, old, new in MUTATIONS[mutation_id]:
    with open(path, encoding="utf-8") as handle:
        text = handle.read()
    count = text.count(old)
    if count != 1:
        sys.exit(f"mutation {mutation_id} matched {count} times in {path}")
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text.replace(old, new))
print(f"applied {mutation_id}")
PY
}

list_mutations() {
  python3 - <<'PY'
import re
with open(__import__("pathlib").Path("tools/mutation_check.sh"), encoding="utf-8") as handle:
    text = handle.read()
print("\n".join(re.findall(r'^\s{4}"([a-z0-9-]+)":', text, re.M)))
PY
}

revert() {
  git checkout -- app/src/main 2>/dev/null
  local dirty
  dirty="$(git status --porcelain -- app/src/main)"
  if [ -n "$dirty" ]; then
    echo "REVERT FAILED, app/src/main still dirty:" >&2
    echo "$dirty" >&2
    exit 3
  fi
}

if [ "${1:-}" = "--list" ]; then
  printf '%-34s %s\n' "MUTATION" "EXPECTED CATCHER"
  while read -r id; do
    printf '%-34s %s\n' "$id" "$(catchers_for "$id")"
  done < <(list_mutations)
  exit 0
fi

MUTATION="${MUTATION:?usage: MUTATION=<id> tools/mutation_check.sh   (or --list)}"
if [ -n "$(git status --porcelain -- app/src/main)" ]; then
  echo "refusing to start: app/src/main is dirty" >&2
  exit 2
fi

set +e
apply_mutation
if [ $? -ne 0 ]; then
  echo "mutation did not apply" >&2
  exit 4
fi

mapfile -t CATCHERS < <(catchers_for "$MUTATION" | tr ' ' '\n' | grep -v '^$')

if [ "${#CATCHERS[@]}" -eq 0 ]; then
  echo "no runnable catcher: $MUTATION is guarded by app/src/androidTest only (a device is not available here)"
  ./gradlew -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8' \
    :app:compileDevDebugKotlin :app:compileDevDebugAndroidTestKotlin --max-workers=3 \
    >/tmp/mutation-gradle.log 2>&1
  if [ $? -ne 0 ]; then
    echo "verdict: DID-NOT-COMPILE - not coverage; pick another mutation"
    tail -20 /tmp/mutation-gradle.log
    revert
    exit 6
  fi
  echo "verdict: UNVERIFIED-IN-THIS-ENVIRONMENT (the edit compiles; its catcher is instrumentation-only)"
  revert
  exit 5
fi

TEST_ARGS=()
for catcher in "${CATCHERS[@]}"; do
  TEST_ARGS+=(--tests "$catcher")
done

rm -rf app/build/test-results/testDevDebugUnitTest
./gradlew -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8' \
  :app:testDevDebugUnitTest --max-workers=3 "${TEST_ARGS[@]}" >/tmp/mutation-gradle.log 2>&1
STATUS=$?

if grep -qE '(^| )e: |Compilation error' /tmp/mutation-gradle.log; then
  echo "verdict: DID-NOT-COMPILE - not coverage; pick another mutation"
  tail -20 /tmp/mutation-gradle.log
  revert
  exit 6
fi

python3 - "$MUTATION" "$STATUS" <<'PY'
import glob, sys, xml.etree.ElementTree as ET

mutation, status = sys.argv[1], int(sys.argv[2])
failures = []
for path in sorted(glob.glob("app/build/test-results/testDevDebugUnitTest/*.xml")):
    for case in ET.parse(path).getroot().iter("testcase"):
        for child in case:
            if child.tag in ("failure", "error"):
                failures.append(f"{case.get('classname')}.{case.get('name')}")
if failures:
    print("verdict: CAUGHT")
    for name in failures:
        print(f"  failed: {name}")
elif status == 0:
    print("verdict: SURVIVED")
else:
    print(f"verdict: INCONCLUSIVE (gradle exit {status} without a test failure)")
PY
echo "gradle exit: $STATUS"
revert
exit 0
