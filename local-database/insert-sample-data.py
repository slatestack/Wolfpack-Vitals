# Christian Harrison - 10/03/2026 - The Four Aces
import sqlite3
import csv

conn = sqlite3.connect("app.db")

cur = conn.cursor()

with open("patient-16-data/Dexcom_016.csv") as csvfile:
    readfile = csv.DictReader(csvfile, delimiter=',')
    for row in readfile:
        sqlString = f"INSERT INTO dexcom VALUES ('{row['Timestamp']}', '{row['Event Type']}', '{row['Event Subtype']}', '{row['Patient Info']}', '{row['Device Info']}', '{row['Source Device ID']}', '{row['Glucose Value']}', '{row['Insulin Value']}', '{row['Carb Value']}', '{row['Duration']}', '{row['Glucose Rate of Change']}', '{row['Transmitter Time']}')"
        cur.execute(sqlString)
        conn.commit()
    readfile.close()

with open("patient-16-data/ACC_016.csv") as csvfile:
    readfile = csv.DictReader(csvfile, delimiter=',')
    for row in readfile:
        sqlString = f"INSERT INTO acc VALUES ('{row['datetime']}', '{row['acc_x']}', '{row['acc_y']}', '{row['acc_z']}')"
        cur.execute(sqlString)
        conn.commit()
    readfile.close()

with open("patient-16-data/EDA_016.csv") as csvfile:
    readfile = csv.DictReader(csvfile, delimiter=',')
    for row in readfile:
        sqlString = f"INSERT INTO eda VALUES ('{row['datetime']}', '{row['eda']}')"
        cur.execute(sqlString)
        conn.commit()
    readfile.close()

with open("patient-16-data/HR_016.csv") as csvfile:
    readfile = csv.DictReader(csvfile, delimiter=',')
    for row in readfile:
        sqlString = f"INSERT INTO hr VALUES ('{row['datetime']}', '{row['hr']}')"
        cur.execute(sqlString)
        conn.commit()
    readfile.close()

with open("patient-16-data/IBI_016.csv") as csvfile:
    readfile = csv.DictReader(csvfile, delimiter=',')
    for row in readfile:
        sqlString = f"INSERT INTO ibi VALUES ('{row['datetime']}', '{row['ibi']}')"
        cur.execute(sqlString)
        conn.commit()
    readfile.close()


conn.close()