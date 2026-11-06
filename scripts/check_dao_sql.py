#!/usr/bin/env python3
"""Executes the REAL @Query strings from SessionDao.kt against SQLite using the schema Room derives from the entities.
Catches typos / wrong column names / wrong semantics that Room's compile-time check would otherwise only reveal in CI."""
import re, sqlite3, pathlib, sys

root = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/kotlin/com/neelaxmi/focus/data"
dao = (root / "SessionDao.kt").read_text()
queries = re.findall(r'@Query\("([^"]+)"\)\s*abstract fun (\w+)', dao)
q = {name: sql for sql, name in queries}
assert set(q) == {"active", "maxId", "finishedSince", "countLogs", "logsFor"}, set(q)

# Schema as Room would generate it from Entities.kt (NOT NULL for non-null Kotlin types).
cols = [("id","INTEGER PRIMARY KEY NOT NULL"),("mode","TEXT NOT NULL"),("state","TEXT NOT NULL"),("phase","TEXT NOT NULL"),
 ("plannedMs","INTEGER"),("pomoFocusMs","INTEGER"),("pomoShortMs","INTEGER"),("pomoLongMs","INTEGER"),("pomoRounds","INTEGER"),("pomoCycles","INTEGER"),
 ("breaksAllowed","INTEGER NOT NULL"),("breakDurationMs","INTEGER NOT NULL"),("strictCooldownMs","INTEGER"),("strictEmergencyLeft","INTEGER"),
 ("startedAtWall","INTEGER NOT NULL"),("endedAtWall","INTEGER"),("focusMs","INTEGER NOT NULL"),("phaseElapsedMs","INTEGER NOT NULL"),
 ("round","INTEGER NOT NULL"),("cycle","INTEGER NOT NULL"),("breaksTaken","INTEGER NOT NULL"),("tickWall","INTEGER NOT NULL"),("tickMono","INTEGER NOT NULL"),
 ("endReqWall","INTEGER"),("endReqMono","INTEGER"),("clockAdjusted","INTEGER NOT NULL"),("rebootCount","INTEGER NOT NULL"),("tagId","INTEGER")]
# every column in the DDL must exist in the Kotlin entity (and vice versa)
ent = (root / "Entities.kt").read_text()
kt_cols = re.findall(r'val (\w+): ', ent.split("class SessionLogEntity")[0])
assert [c for c,_ in cols] == kt_cols, (set(kt_cols) ^ {c for c,_ in cols})

db = sqlite3.connect(":memory:")
db.execute("CREATE TABLE sessions (%s)" % ", ".join(f"{c} {t}" for c, t in cols))
db.execute("CREATE TABLE session_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId INTEGER NOT NULL, atWall INTEGER NOT NULL, kind TEXT NOT NULL, phase TEXT)")

def named(sql):  # Room ':param' -> sqlite named params
    return sql
def ins(id, state, started):
    db.execute("INSERT INTO sessions VALUES (%s)" % ",".join("?"*len(cols)),
      [id,"TIMER",state,"FOCUS",1500000,None,None,None,None,None,0,0,None,None,started,None,0,0,1,1,0,started,0,None,None,0,0,None])

def run(name, **p):
    return db.execute(q[name], p).fetchall()

assert run("maxId") == [(0,)], "maxId on empty table must be 0 (COALESCE)"
assert run("active") == []
ins(1, "COMPLETED", 1000); ins(2, "ABANDONED", 3000); ins(3, "COMPLETED", 2000); ins(4, "PAUSED", 4000); ins(5, "COMPLETED", 500)
assert run("maxId") == [(5,)]
act = run("active"); assert len(act) == 1 and act[0][0] == 4 and act[0][2] == "PAUSED", act
ins(6, "RUNNING", 5000); assert run("active")[0][0] == 6, "newest active wins"
fin = run("finishedSince", since=1000); assert [r[0] for r in fin] == [2, 3, 1], [r[0] for r in fin]   # newest first, excludes active + too old
assert all(r[2] in ("COMPLETED","ABANDONED") for r in fin)
for k, t in (("STARTED",10),("EMERGENCY_EXIT",100),("EMERGENCY_EXIT",200),("EMERGENCY_EXIT",50),("EARLY_EXIT",300)):
    db.execute("INSERT INTO session_logs(sessionId,atWall,kind,phase) VALUES (1,?,?,NULL)", (t, k))
assert run("countLogs", kind="EMERGENCY_EXIT", since=100) == [(2,)]
assert run("countLogs", kind="EMERGENCY_EXIT", since=0) == [(3,)]
assert run("countLogs", kind="NOPE", since=0) == [(0,)]
assert [r[2] for r in run("logsFor", sessionId=1)] == [10, 50, 100, 200, 300], "logsFor must be ordered by atWall"
assert run("logsFor", sessionId=99) == []
print("DAO SQL OK: %d queries executed against SQLite" % len(q))
