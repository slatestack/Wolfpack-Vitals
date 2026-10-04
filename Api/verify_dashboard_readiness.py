"""Read-only local recording and running API audit. Never assigns timezone or predicts values.
Run with the API listening: python -m Api.verify_dashboard_readiness
"""
import csv
import json
from datetime import datetime, timedelta
from pathlib import Path
import httpx

ROOT = Path(__file__).resolve().parents[1]


def audit(base_url='http://127.0.0.1:8000'):
    sensors = {}
    spans = {}
    mapping = [('hr', 'HR', 'bpm', ['hr']), ('eda', 'EDA', 'uS', ['eda']),
               ('acc', 'ACC', 'device_counts', ['acc_x', 'acc_y', 'acc_z']),
               ('ibi', 'IBI', 's', ['ibi']), ('temp', 'TEMP', 'degC', ['temp']),
               ('glucose', 'Dexcom', 'mg/dL', ['Glucose Value'])]
    for sensor, filename, unit, columns in mapping:
        path = ROOT / 'local-database' / 'patient-16-data' / f'{filename}_016.csv'
        readings = []
        if path.exists():
            with path.open(newline='') as source:
                for row in csv.DictReader(source):
                    if sensor == 'glucose' and row.get('Event Type') != 'EGV':
                        continue
                    timestamp = datetime.fromisoformat(row['Timestamp' if sensor == 'glucose' else 'datetime'])
                    readings.append({'timestamp': timestamp.isoformat(), 'values': [float(row[c]) for c in columns]})
        readings.sort(key=lambda r: r['timestamp'])
        sensors[sensor] = {'unit': unit, 'readings': readings}
        spans[sensor] = {'rows': len(readings), 'unit_label': unit,
                         'first': readings[0]['timestamp'] if readings else None,
                         'last': readings[-1]['timestamp'] if readings else None}
    start = datetime.fromisoformat(sensors['hr']['readings'][0]['timestamp'])
    responses = {}
    with httpx.Client(base_url=base_url, timeout=10) as client:
        readiness = client.get('/analysis_readiness'); readiness.raise_for_status()
        for interval in (1, 2, 12):
            end = start + timedelta(minutes=5*interval)
            window_sensors = {}
            for sensor, series in sensors.items():
                readings = [r for r in series['readings'] if start <= datetime.fromisoformat(r['timestamp']) < end]
                window_sensors[sensor] = dict(series, readings=readings, coverage={
                    'sample_count': len(readings), 'first_timestamp': readings[0]['timestamp'] if readings else None,
                    'last_timestamp': readings[-1]['timestamp'] if readings else None})
            payload = {'patient_id': '16', 'session_id': 'recording-audit', 'interval': interval,
                       'active_elapsed_ms': interval*300000,
                       'source_window': {'id': f'audit:{interval}', 'start': start.isoformat(), 'end': end.isoformat(),
                                         'time_basis': 'source_local_unspecified'}, 'sensors': window_sensors}
            response = client.post('/make_prediction', json=payload); response.raise_for_status()
            responses[str(interval)] = {'coverage': {k: v['coverage']['sample_count'] for k, v in window_sensors.items()},
                                        'reasons': {k: v['reason'] for k, v in response.json()['results'].items()}}
    return {'api_url_tested_on_host': base_url, 'recording_spans': spans,
            'readiness': readiness.json(), 'actual_source_requests': responses,
            'timezone_synchronization_and_patient_mapping': 'Unverified: filenames and CSV unit labels alone are not provenance certification.'}


if __name__ == '__main__':
    print(json.dumps(audit(), indent=2))
