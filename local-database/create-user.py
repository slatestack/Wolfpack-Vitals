import sqlite3
import hashlib
import datetime

conn = sqlite3.connect("app.db")

cur = conn.cursor()

c = hashlib.sha256()

username = "Test"
c.update(b"Test")
c.update(b"1")
password = c.hexdigest()

print(username, password)
