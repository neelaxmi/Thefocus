#!/usr/bin/env python3
"""Room v2 verification without Room:
  1. Parses the @Entity classes and derives the DDL Room generates for them.
  2. Builds a v1 database (Milestone 1 entities) holding real session rows, applies the ACTUAL statements of MIGRATION_1_2,
     and requires the result to be identical (tables, columns, types, NOT NULL, primary keys, indices) to a FRESH v2 database.
  3. Proves no v1 data is lost.
  4. Executes every new @Query on the migrated database and checks its semantics.
Limits: this validates my model of Room's DDL, not Room itself. The androidTest MigrationTest runs the real thing in CI."""
import pathlib, re, sqlite3, sys

root = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/kotlin/com/neelaxmi/focus/data"
SQLTYPE = {"Long": "INTEGER", "Int": "INTEGER", "Boolean": "INTEGER", "String": "TEXT"}

def balanced(text, open_idx):
    """Return the substring inside the parenthesis that opens at text[open_idx] (handles nesting and quotes)."""
    assert text[open_idx] == "("
    depth, i, in_str = 0, open_idx, False
    while i < len(text):
        c = text[i]
        if c == '"' and text[i - 1] != "\\": in_str = not in_str
        elif not in_str:
            if c == "(": depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0: return text[open_idx + 1:i], i
        i += 1
    raise SystemExit("unbalanced parentheses")

def parse_entities(text):
    out = []
    for m in re.finditer(r'@Entity\(', text):
        ann, end = balanced(text, m.end() - 1)
        cm = re.compile(r'\s*data class (\w+)\(').match(text, end + 1)
        assert cm, "@Entity not followed by a data class"
        body, _ = balanced(text, cm.end() - 1)
        table = re.search(r'tableName = "(\w+)"', ann).group(1)
        pks = re.search(r'primaryKeys = \[(.*?)\]', ann)
        pks = re.findall(r'"(\w+)"', pks.group(1)) if pks else []
        idx = [re.findall(r'"(\w+)"', i) for i in re.findall(r'Index\((.*?)\)', ann)]
        cols, auto = [], None
        for fm in re.finditer(r'(@PrimaryKey(?:\(autoGenerate = true\))?)?\s*val (\w+): (\w+)(\?)?', body):
            pk, name, typ, nullable = fm.groups()
            cols.append((name, SQLTYPE[typ], not nullable))
            if pk:
                pks = [name]
                if "autoGenerate" in pk: auto = name
        out.append(dict(table=table, cols=cols, pks=pks, auto=auto, indices=idx))
    return out

def ddl(e):
    parts = []
    for name, typ, notnull in e["cols"]:
        if name == e["auto"]: parts.append(f"`{name}` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL")
        else: parts.append(f"`{name}` {typ}" + (" NOT NULL" if notnull else ""))
    if e["pks"] and not e["auto"]: parts.append("PRIMARY KEY(" + ", ".join(f"`{p}`" for p in e["pks"]) + ")")
    stmts = [f"CREATE TABLE IF NOT EXISTS `{e['table']}` (" + ", ".join(parts) + ")"]
    for cols in e["indices"]:
        stmts.append(f"CREATE INDEX IF NOT EXISTS `index_{e['table']}_{'_'.join(cols)}` ON `{e['table']}` (" + ", ".join(f"`{c}`" for c in cols) + ")")
    return stmts

v1 = parse_entities((root / "Entities.kt").read_text())
v2_new = parse_entities((root / "EntitiesM2.kt").read_text())
assert {e["table"] for e in v1} == {"sessions", "session_logs"}, [e["table"] for e in v1]
assert [e["table"] for e in v2_new] == ["block_groups","block_group_apps","schedules","schedule_groups","schedule_runs","achievements","daily_summary","daily_app_usage"], [e["table"] for e in v2_new]
assert {e["table"]: len(e["cols"]) for e in v1 + v2_new} == {"sessions":28,"session_logs":5,"block_groups":5,"block_group_apps":2,"schedules":8,"schedule_groups":2,"schedule_runs":3,"achievements":2,"daily_summary":4,"daily_app_usage":3}, {e["table"]: len(e["cols"]) for e in v1 + v2_new}
# the database class must list every entity (a forgotten entity would silently have no table)
dbtext = (root / "FocusDatabase.kt").read_text()
for e in re.findall(r'(\w+Entity)::class', dbtext): pass
listed = set(re.findall(r'(\w+Entity)::class', dbtext))
declared = set(re.findall(r'data class (\w+Entity)\(', (root / "Entities.kt").read_text() + (root / "EntitiesM2.kt").read_text()))
assert listed == declared, ("entities not registered in @Database:", declared ^ listed)
assert "version = 2" in dbtext

def create(stmts):
    db = sqlite3.connect(":memory:")
    for s in stmts: db.execute(s)
    return db

fresh = create([s for e in v1 + v2_new for s in ddl(e)])

# ---- migrated database: v1 + real data, then the ACTUAL migration statements ----
mig = create([s for e in v1 for s in ddl(e)])
mig.execute("INSERT INTO sessions VALUES (%s)" % ",".join(["?"] * 28),
    [7, "TIMER", "COMPLETED", "FOCUS", 1500000, None, None, None, None, None, 0, 0, None, None, 1000, 2000, 1500000, 0, 1, 1, 0, 0, 0, None, None, 0, 0, None])
mig.execute("INSERT INTO session_logs(sessionId,atWall,kind,phase) VALUES (7, 1000, 'STARTED', 'FOCUS')")
migtext = (root / "Migrations.kt").read_text()
stmts = re.findall(r'db\.execSQL\("([^"]+)"\)', migtext)
assert len(stmts) == 9, len(stmts)
assert "Migration(1, 2)" in migtext and "ALL_MIGRATIONS" in migtext
for s in stmts: mig.execute(s)

def schema(db):
    res = {}
    for (t,) in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").fetchall():
        cols = sorted((r[1], r[2].upper(), r[3], r[5]) for r in db.execute(f"PRAGMA table_info(`{t}`)"))     # name,type,notnull,pk position
        idx = sorted((r[1], r[2], tuple(c[2] for c in db.execute(f"PRAGMA index_info(`{r[1]}`)"))) for r in db.execute(f"PRAGMA index_list(`{t}`)") if r[3] == "c")
        res[t] = (cols, idx)
    return res

a, b = schema(fresh), schema(mig)
assert a.keys() == b.keys(), ("table sets differ", sorted(a.keys() ^ b.keys()))
for t in a:
    assert a[t] == b[t], f"MIGRATION MISMATCH in table {t}:\n fresh   = {a[t]}\n migrated= {b[t]}"
print(f"migration schema == fresh v2 schema for all {len(a)} tables")
assert mig.execute("SELECT COUNT(*) FROM sessions").fetchone() == (1,) and mig.execute("SELECT focusMs FROM sessions WHERE id=7").fetchone() == (1500000,)
assert mig.execute("SELECT COUNT(*) FROM session_logs").fetchone() == (1,)
print("v1 session data preserved by the migration")
# running the migration twice must be harmless (IF NOT EXISTS)
for s in stmts: mig.execute(s)

# ---- every new @Query exists, is covered below, and behaves ----
def queries(file):
    return {n: q for q, n in re.findall(r'@Query\("([^"]+)"\)\s*abstract fun (\w+)', (root / file).read_text())}
cq, uq = queries("ConfigDao.kt"), queries("UsageDao.kt")
assert set(cq) == {"groups","groupApps","groupCount","setGroupEnabled","renameGroup","deleteGroupRow","deleteGroupApps","deleteScheduleLinksForGroup",
                   "schedules","scheduleGroups","deleteScheduleGroups","setScheduleEnabled","deleteScheduleRow","deleteRuns","pruneRuns"}, set(cq)
assert set(uq) == {"deleteAppsForDay","summaries","appUsage","firstDay","achievements"}, set(uq)
db = mig
def run(q, **p): return db.execute(q, p).fetchall()

# groups
for i, (n, k, en, so) in enumerate([("Games","GAMES",1,2),("Social","SOCIAL",1,1),("Misc","CUSTOM",0,1)], start=1):
    db.execute("INSERT INTO block_groups(name,kind,enabled,sortOrder) VALUES (?,?,?,?)", (n,k,en,so))
assert [r[1] for r in run(cq["groups"])] == ["Social", "Misc", "Games"], "ordered by sortOrder then id"
assert run(cq["groupCount"]) == [(3,)]
run(cq["setGroupEnabled"], id=3, enabled=1); assert db.execute("SELECT enabled FROM block_groups WHERE id=3").fetchone() == (1,)
run(cq["renameGroup"], id=3, name="Mine"); assert db.execute("SELECT name FROM block_groups WHERE id=3").fetchone() == ("Mine",)
for g, p in [(1,"a"),(1,"b"),(2,"c")]: db.execute("INSERT INTO block_group_apps VALUES (?,?)", (g, p))
assert len(run(cq["groupApps"])) == 3
try: db.execute("INSERT INTO block_group_apps VALUES (1,'a')"); raise SystemExit("composite PK must reject a duplicate app in a group")
except sqlite3.IntegrityError: pass
db.execute("INSERT INTO schedules(name,daysMask,startMin,endMin,enabled,strict,autoStart) VALUES ('Night',31,1320,360,1,1,1),('Morning',31,540,660,1,0,1)")
assert [r[1] for r in run(cq["schedules"])] == ["Morning", "Night"], "ordered by startMin"
db.execute("INSERT INTO schedule_groups VALUES (1,1),(1,2),(2,1)")
assert len(run(cq["scheduleGroups"])) == 3
run(cq["deleteScheduleLinksForGroup"], groupId=1); assert run(cq["scheduleGroups"]) == [(1, 2)]
db.execute("INSERT INTO schedule_groups VALUES (2,2)")
run(cq["deleteScheduleGroups"], scheduleId=2); assert run(cq["scheduleGroups"]) == [(1, 2)], "only schedule 2's links removed"
run(cq["deleteGroupApps"], groupId=1); assert len(run(cq["groupApps"])) == 1
run(cq["deleteGroupRow"], id=1); assert db.execute("SELECT COUNT(*) FROM block_groups").fetchone() == (2,)
run(cq["setScheduleEnabled"], id=1, enabled=0); assert db.execute("SELECT enabled FROM schedules WHERE id=1").fetchone() == (0,)
# idempotent runs: INSERT OR IGNORE on the composite key (what Room generates for OnConflictStrategy.IGNORE)
ins = "INSERT OR IGNORE INTO schedule_runs VALUES (?,?,?)"
assert db.execute(ins, (2, "S:2026-11-05", 100)).rowcount == 1 and db.execute(ins, (2, "S:2026-11-05", 999)).rowcount == 0
assert db.execute(ins, (2, "R:2026-11-05", 100)).rowcount == 1 and db.execute(ins, (2, "S:2026-11-06", 100)).rowcount == 1
assert db.execute("SELECT atWall FROM schedule_runs WHERE ruleId=2 AND runKey='S:2026-11-05'").fetchone() == (100,), "first write wins"
run(cq["pruneRuns"], before=101); assert db.execute("SELECT COUNT(*) FROM schedule_runs").fetchone() == (0,)
db.execute(ins, (2, "S:x", 50)); db.execute(ins, (2, "S:y", 500)); run(cq["pruneRuns"], before=100); assert db.execute("SELECT COUNT(*) FROM schedule_runs").fetchone() == (1,)
db.execute(ins, (9, "S:z", 1)); run(cq["deleteRuns"], ruleId=9); assert db.execute("SELECT COUNT(*) FROM schedule_runs WHERE ruleId=9").fetchone() == (0,)
run(cq["deleteScheduleRow"], id=2); assert db.execute("SELECT COUNT(*) FROM schedules").fetchone() == (1,)

# usage
for d, tot in [("2026-11-01", 10), ("2026-11-03", 30), ("2026-11-02", 20), ("2026-12-01", 99)]:
    db.execute("INSERT INTO daily_summary VALUES (?,?,?,?)", (d, tot, 1, 0))
    db.execute("INSERT INTO daily_app_usage VALUES (?,?,?)", (d, "a", tot)); db.execute("INSERT INTO daily_app_usage VALUES (?,?,?)", (d, "b", tot))
assert [r[0] for r in run(uq["summaries"], **{"from": "2026-11-01", "to": "2026-11-03"})] == ["2026-11-01", "2026-11-02", "2026-11-03"], "inclusive range, chronological"
assert len(run(uq["appUsage"], **{"from": "2026-11-02", "to": "2026-11-02"})) == 2
assert run(uq["firstDay"]) == [("2026-11-01",)]
# replaceDay semantics: stale per-app rows must not survive a recompute
run(uq["deleteAppsForDay"], dateKey="2026-11-02"); db.execute("INSERT OR REPLACE INTO daily_summary VALUES ('2026-11-02', 5, 0, 1)"); db.execute("INSERT INTO daily_app_usage VALUES ('2026-11-02','a',5)")
assert db.execute("SELECT pkg FROM daily_app_usage WHERE dateKey='2026-11-02'").fetchall() == [("a",)], "app 'b' removed by recompute"
assert run(uq["achievements"]) == []
ins2 = "INSERT OR IGNORE INTO achievements VALUES (?,?)"
assert db.execute(ins2, ("FOCUS_1H", 1)).rowcount == 1 and db.execute(ins2, ("FOCUS_1H", 2)).rowcount == 0 and db.execute("SELECT unlockedAtWall FROM achievements").fetchone() == (1,)
print("Room v2 checks OK: %d config queries + %d usage queries executed" % (len(cq), len(uq)))
