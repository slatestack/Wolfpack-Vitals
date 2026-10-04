"""Read-only capability audit; outputs names and aggregate coverage, never tokens or patient readings.
Run: python -m Api.verify_databricks > /tmp/databricks-audit.json
"""
import json
import os
import time
from datetime import datetime, timezone
from pathlib import Path
import httpx
from dotenv import load_dotenv


def audit():
    load_dotenv(Path(__file__).with_name('.env'))
    host = os.environ['DATABRICKS_HOST'].rstrip('/')
    if not host.startswith('https://'):
        raise ValueError('Databricks requires HTTPS')
    with httpx.Client(base_url=host, headers={'Authorization': 'Bearer '+os.environ['DATABRICKS_TOKEN']}, timeout=20) as client:
        def get(path, params=None):
            response = client.get(path, params=params)
            response.raise_for_status()
            return response.json()
        # Paginate so the audit never equates a partial first page with absence.
        def listing(path, key, params=None):
            parameters = dict(params or {})
            items = []
            for _ in range(100):
                data = get(path, parameters)
                items.extend(data.get(key, []))
                if not data.get('next_page_token'):
                    return items
                parameters['page_token'] = data['next_page_token']
            raise ValueError('Capability listing exceeded audit limit')
        schema = {'catalog_name':'workspace', 'schema_name':'wolfpack-vitals'}
        tables = listing('/api/2.1/unity-catalog/tables', 'tables', schema)
        functions = listing('/api/2.1/unity-catalog/functions', 'functions', schema)
        models = listing('/api/2.1/unity-catalog/models', 'registered_models', schema)
        endpoints = listing('/api/2.0/serving-endpoints', 'endpoints')
        report = {'verified_at':datetime.now(timezone.utc).isoformat(), 'scope':'workspace.wolfpack-vitals and accessible serving endpoints',
            'tables':[{ 'name':t['full_name'], 'columns':[c['name'] for c in t.get('columns',[])]} for t in tables],
            'functions':[f['full_name'] for f in functions], 'registered_models':[m.get('full_name',m.get('name')) for m in models],
            'endpoints':[{ 'name':e['name'], 'type':e.get('endpoint_type'), 'ready':e.get('state',{}).get('ready')} for e in endpoints],
            'jobs':[j.get('settings',{}).get('name') for j in listing('/api/2.1/jobs/list','jobs')],
            'pipelines':[p.get('name') for p in listing('/api/2.0/pipelines','statuses')]}
        warehouses = listing('/api/2.0/sql/warehouses', 'warehouses')
        warehouse = os.getenv('DATABRICKS_WAREHOUSE_ID') or next((w['id'] for w in warehouses if w.get('enable_serverless_compute')), None)
        if warehouse:
            query = """SELECT 'hr' sensor, min(patientNumber) first_patient, max(patientNumber) last_patient,
                count(DISTINCT patientNumber) patients, count_if(patientNumber = 16) patient_16_rows FROM workspace.`wolfpack-vitals`.hr_values
                UNION ALL SELECT 'eda', min(patientNumber), max(patientNumber), count(DISTINCT patientNumber), count_if(patientNumber = 16) FROM workspace.`wolfpack-vitals`.eda_values
                UNION ALL SELECT 'acc', min(patientNumber), max(patientNumber), count(DISTINCT patientNumber), count_if(patientNumber = 16) FROM workspace.`wolfpack-vitals`.acc_values
                UNION ALL SELECT 'ibi', min(patientNumber), max(patientNumber), count(DISTINCT patientNumber), count_if(patientNumber = 16) FROM workspace.`wolfpack-vitals`.ibi_values
                UNION ALL SELECT 'glucose', min(patientNumber), max(patientNumber), count(DISTINCT patientNumber), count_if(patientNumber = 16) FROM workspace.`wolfpack-vitals`.dexcom_values WHERE `Event Type` = 'EGV'
                UNION ALL SELECT 'carbohydrate_events', min(patientNumber), max(patientNumber), count(DISTINCT patientNumber), count_if(patientNumber = 16) FROM workspace.`wolfpack-vitals`.dexcom_values WHERE try_cast(`Carb Value (grams)` AS DOUBLE) IS NOT NULL"""
            response = client.post('/api/2.0/sql/statements',json={'warehouse_id':warehouse,'statement':query,'wait_timeout':'10s'})
            response.raise_for_status()
            statement = response.json()
            deadline = time.monotonic()+60
            while statement['status']['state'] in ('PENDING','RUNNING') and time.monotonic()<deadline:
                time.sleep(2)
                statement=get('/api/2.0/sql/statements/'+statement['statement_id'])
            report['coverage_query_status']=statement['status']['state']
            if statement['status']['state']=='SUCCEEDED':
                names=[c['name'] for c in statement['manifest']['schema']['columns']]
                report['patient_coverage']=[dict(zip(names,row)) for row in statement.get('result',{}).get('data_array',[])]
            else:
                client.post('/api/2.0/sql/statements/'+statement['statement_id']+'/cancel')
        return report

if __name__ == '__main__':
    print(json.dumps(audit(),indent=2))
