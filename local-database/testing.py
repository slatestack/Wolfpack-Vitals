# Christian Harrison - 10/03/2026 - The Four Aces
import sqlite3
from time import sleep
import datetime as dt
import csv

conn = sqlite3.connect("app.db")

cur = conn.cursor()

table = "hr"
variable1 = "hr"
condition = ">"
variable2 = "80"

sqlString = f"SELECT * from {table} WHERE {variable1} {condition} {variable2}"

res = cur.execute(sqlString)

print(res.fetchall())
    