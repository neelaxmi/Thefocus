#!/usr/bin/env bash
# Everything that can be verified WITHOUT Gradle / the Android SDK / Maven. Needs: kotlinc (2.x), JDK 17+, python3,
# and an android.jar (API >= 34) for the framework compile check.
#   KOTLINC=/path/to/kotlinc ANDROID_JAR=/path/to/android.jar bash scripts/verify_offline.sh
# This does NOT replace CI: Gradle sync, Compose, Room code generation, lint and emulator tests only run there.
# Every compile goes through compile() in lib.sh, so a step is only reported as "compiled OK" if kotlinc exited 0
# and produced class files; any failure aborts the script with a non-zero exit status.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh
ANDROID_JAR="${ANDROID_JAR:?set ANDROID_JAR=/path/to/android.jar}"
[ -f "$ANDROID_JAR" ] || { echo "ERROR: ANDROID_JAR not found: $ANDROID_JAR" >&2; exit 2; }
W="$(mktemp -d)"

echo "[1/7] engine: compile + run suite (>= $MIN_ENGINE_CHECKS checks required)"
compile "engine (main + tests)" "$W/engine" core-engine/src/main/kotlin $(engine_test_sources)
run_engine_suite "$W/engine"
# main-only classes: what the Android modules actually depend on
compile "engine (main only)" "$W/engine_main" core-engine/src/main/kotlin

echo "[2/7] resources: well-formed XML, string cross-check, generated R stub"
python3 - <<'PY'
import glob, re, xml.dom.minidom as m
for f in glob.glob('app/src/main/res/**/*.xml', recursive=True) + ['app/src/main/AndroidManifest.xml']: m.parse(f)
print("  all XML well-formed")
xml = open('app/src/main/res/values/strings.xml').read(); d = set(re.findall(r'<string name="([^"]+)"', xml)); u = set()
for f in glob.glob('app/src/main/kotlin/**/*.kt', recursive=True): u |= set(re.findall(r'R\.string\.(\w+)', open(f).read()))
assert not (u - d), "undefined strings: %s" % sorted(u - d)
print("  every R.string used in code is defined (%d)" % len(u))
for n, a, v in re.findall(r'<string name="([^"]+)"([^>]*)>(.*?)</string>', xml, re.S):
    assert not re.search(r'(?<!\\)"', v), "unescaped double quote in string " + n          # aapt2 would silently strip it
    assert not re.search(r"(?<!\\)'", v), "unescaped apostrophe in string " + n
    if 'formatted="false"' not in a: assert not re.search(r'%(?!\d\$[sdf]|\d\$\.\d[f]|%)', v), "non-positional % in " + n
print("  string resources: quotes, apostrophes and format specifiers are valid")
PY
python3 - "$W/R.kt" <<'PY'
import os, re, sys
res = 'app/src/main/res'; g = {}
for root, _, files in os.walk(res):
    kind = os.path.basename(root).split('-')[0]
    for f in files:
        if kind == 'values':
            t = open(os.path.join(root, f)).read()
            for tag in ('string', 'color', 'style'):
                for n in re.findall(r'<%s\s+name="([^"]+)"' % tag, t): g.setdefault(tag, set()).add(n.replace('.', '_'))
        elif kind in ('drawable', 'mipmap', 'xml'): g.setdefault(kind, set()).add(f.rsplit('.', 1)[0])
out = ['package com.neelaxmi.focus', 'object R {']; i = 1
for k, v in sorted(g.items()):
    out.append('object %s {' % k)
    for n in sorted(v): out.append('const val %s = %d' % (n, i)); i += 1
    out.append('}')
out.append('}'); open(sys.argv[1], 'w').write('\n'.join(out))
PY

echo "[3/7] platform layer (service, alarms, receivers, notifications) vs the real android.jar"
compile "platform layer" "$W/platform" -cp "$ANDROID_JAR:$W/engine_main" "$W/R.kt" app/src/main/kotlin/com/neelaxmi/focus/platform

echo "[4/7] data layer type-check (Room ANNOTATION STUBS only) + mapper tests (JUnit shim)"
mkdir -p "$W/stub" "$W/shim/org/junit"
cat > "$W/stub/S.kt" <<'KT'
package androidx.room
import kotlin.reflect.KClass
annotation class Entity(val tableName: String = "", val indices: Array<Index> = [], val primaryKeys: Array<String> = [])
annotation class Index(vararg val value: String)
annotation class PrimaryKey(val autoGenerate: Boolean = false)
annotation class Dao
annotation class Query(val value: String)
annotation class Insert(val onConflict: Int = 1)
annotation class Transaction
annotation class Database(val entities: Array<KClass<*>>, val version: Int, val exportSchema: Boolean = true)
object OnConflictStrategy { const val REPLACE = 1; const val IGNORE = 5 }
abstract class RoomDatabase { abstract class Builder<T : RoomDatabase> { fun addMigrations(vararg m: androidx.room.migration.Migration): Builder<T> = this; fun build(): T = throw NotImplementedError() } }
object Room { fun <T : RoomDatabase> databaseBuilder(ctx: android.content.Context, k: Class<T>, name: String): RoomDatabase.Builder<T> = throw NotImplementedError() }
KT
mkdir -p "$W/stub2/androidx/room/migration" "$W/stub2/androidx/sqlite/db"
cat > "$W/stub2/androidx/room/migration/M.kt" <<'KT'
package androidx.room.migration
import androidx.sqlite.db.SupportSQLiteDatabase
abstract class Migration(val startVersion: Int, val endVersion: Int) { abstract fun migrate(db: SupportSQLiteDatabase) }
KT
cat > "$W/stub2/androidx/sqlite/db/S.kt" <<'KT'
package androidx.sqlite.db
interface SupportSQLiteDatabase { fun execSQL(sql: String) }
KT
cat > "$W/shim/org/junit/J.kt" <<'KT'
package org.junit
annotation class Test
object Assert {
    fun assertEquals(a: Any?, b: Any?) { if (a != b) throw AssertionError("expected <$a> but was <$b>") }
    fun assertNull(a: Any?) { if (a != null) throw AssertionError("expected null but was <$a>") }
}
KT
cat > "$W/shim/Run.kt" <<'KT'
import org.junit.Test
fun main() {
    val c = Class.forName("com.neelaxmi.focus.data.SessionMappersTest"); var f = 0; var n = 0
    for (m in c.declaredMethods.filter { it.isAnnotationPresent(Test::class.java) }) {
        n++; try { m.invoke(c.getDeclaredConstructor().newInstance()) } catch (e: Exception) { f++; println("FAIL ${m.name}: ${e.cause ?: e}") }
    }
    println("  mapper tests: $n run, $f failed")
    if (n == 0 || f > 0) kotlin.system.exitProcess(1)
}
KT
compile "data layer + mapper tests" "$W/data" -cp "$ANDROID_JAR:$W/engine_main" "$W/stub/S.kt" "$W/stub2/androidx/room/migration/M.kt" "$W/stub2/androidx/sqlite/db/S.kt" "$W/shim/org/junit/J.kt" "$W/shim/Run.kt" app/src/main/kotlin/com/neelaxmi/focus/data app/src/test/kotlin/com/neelaxmi/focus/data
java -cp "$W/data:$W/engine_main:$STDLIB" RunKt

echo "[5/7] application shell: FocusApp + platform + data type-checked together (Room = stubs, framework = real android.jar)"
compile "app shell (FocusApp, platform, data)" "$W/shell" -cp "$ANDROID_JAR:$W/engine_main" "$W/R.kt" "$W/stub/S.kt" "$W/stub2/androidx/room/migration/M.kt" "$W/stub2/androidx/sqlite/db/S.kt" app/src/main/kotlin/com/neelaxmi/focus/platform app/src/main/kotlin/com/neelaxmi/focus/data app/src/main/kotlin/com/neelaxmi/focus/FocusApp.kt

echo "[6/7] DAO SQL (v1) + Room v2 migration/queries executed against SQLite"
python3 scripts/check_dao_sql.py
python3 scripts/check_room_v2.py

echo "[7/7] manifest cross-checks"
python3 - <<'PY'
import glob, re
m = open('app/src/main/AndroidManifest.xml').read()
src = ''.join(open(f).read() for f in glob.glob('app/src/main/kotlin/**/*.kt', recursive=True))
for n in re.findall(r'android:name="(\.[\w.]+)"', m): assert re.search(r'class %s\b' % n.split('.')[-1], src), "missing class for " + n
perms = re.findall(r'<uses-permission[^>]*android:name="android.permission.(\w+)"', m)
assert not any(p in ('INTERNET', 'ACCESS_NETWORK_STATE') for p in perms), perms
print("  manifest components exist; permissions =", perms)
PY
echo "ALL OFFLINE CHECKS PASSED"
