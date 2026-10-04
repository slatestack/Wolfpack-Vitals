# Christian Harrison - 10/03/2026 - The Four Aces
import sqlite3

# Creates database "app.db"
conn = sqlite3.connect("app.db")

cur = conn.cursor()

# Accelerometer Table
cur.execute(
    "CREATE TABLE acc(datetime TEXT, acc_x INTEGER, acc_y INTEGER, acc_z INTEGER)"
)
# Glucose Concentration Table
cur.execute(
    "CREATE TABLE dexcom(datetime, eventType, eventSubtype, patientInfo, deviceInfo, sourceDeviceID, glucoseValue, insulinValue, carbValue, duration, glucoseRateOfChange, transmitterTime)"
)
# Electrodermal Activity Table
cur.execute(
    "CREATE TABLE eda(datetime TEXT, eda INTEGER)"
)
# Heart Rate Table
cur.execute(
    "CREATE TABLE hr(datetime TEXT, hr INTEGER)"
)
# Interbeat Interval Table
cur.execute(
    "CREATE TABLE ibi(datetime TEXT, ibi INTEGER)"
)
# User Credentials Table
cur.execute(
    "CREATE TABLE login(username TEXT, password TEXT)"
)

cur.close()
