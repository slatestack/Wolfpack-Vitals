# Christian Harrison - 10/03/2026 - The Four Aces
import sqlite3

conn = sqlite3.connect("app.db")

cur = conn.cursor()

cur.execute(
    "DROP TABLE acc"
)

cur.execute(
    "DROP TABLE dexcom"
)
cur.execute(
    "DROP TABLE eda"
)
cur.execute(
    "DROP TABLE hr"
)
cur.execute(
    "DROP TABLE ibi"
)
cur.execute(
    "DROP TABLE login"
)

cur.close()