import copy
import json
import os
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch
import httpx
from fastapi.testclient import TestClient
from pydantic import ValidationError
from Api.main import app
from Api.contracts import DashboardRequest, MetricResult, METRICS
from Api.dashboard_analysis import DatabricksDashboardAnalyzer, Deployment, MetricPolicy

# Synthetic model fixtures test the contract. They are not clinical thresholds or deployments.
def request_data(utc=True):
    start = datetime(2020, 7, 16, 9, 29, 13, tzinfo=timezone.utc if utc else None)
    readings = [{'timestamp': (start + timedelta(seconds=i)).isoformat(), 'values': [0.0 if i == 0 else 1.0]} for i in range(300)]
    def series(unit, vals=readings):
        return {'unit': unit, 'coverage': {'sample_count': len(vals), 'first_timestamp': vals[0]['timestamp'] if vals else None,
            'last_timestamp': vals[-1]['timestamp'] if vals else None}, 'readings': copy.deepcopy(vals)}
    return {'patient_id': '16', 'session_id': 'test-session', 'interval': 1, 'active_elapsed_ms': 300000,
        'source_window': {'id': 'window-1', 'start': start.isoformat(), 'end': (start+timedelta(minutes=5)).isoformat(),
            'time_basis': 'verified_utc' if utc else 'source_local_unspecified'},
        'sensors': {'eda': series('uS'), 'hr': series('bpm', [dict(r, values=[70.0]) for r in readings]),
            'ibi': series('s', [dict(r, values=[0.8]) for r in readings]),
            'glucose': series('mg/dL', [dict(r, values=[100.0]) for r in readings])},
        'features': {'validated_feature': 3.5}, 'cumulative_replay_averages': {'heartbeat': 71.0}}


def policy_data():
    return {'unit': 'test-unit', 'threshold_version': 'fixture-v1',
        'reference_range': {'label': 'Synthetic test range', 'unit': 'test-unit', 'lower': 0, 'upper': 10},
        'bands': [{'lower': None, 'upper': 10, 'category': 'low', 'bar_position': 0.2},
            {'lower': 10, 'upper': 20, 'category': 'moderate', 'bar_position': 0.6},
            {'lower': 20, 'upper': None, 'category': 'high', 'bar_position': 0.9}],
        'required_sensors': {'glucose': {'unit': 'mg/dL', 'min_samples': 2, 'max_gap_seconds': 2}}}


def config_data():
    return {'endpoint': 'test-dashboard', 'entity_name': 'catalog.schema.test-model', 'entity_version': '7',
        'configuration_source': 'fixture-only:model-config', 'metrics': {'glucose_variability': policy_data()}}


def output(value=10.0, category='moderate', position=0.6, confidence=None):
    return {'availability': 'available', 'value': value, 'unit': 'test-unit', 'risk_category': category,
        'bar_position': position, 'reference_range': policy_data()['reference_range'], 'threshold_version': 'fixture-v1',
        'confidence': confidence, 'model_version': '7', 'window_id': 'window-1', 'risk_probability': 0.3}


class DashboardContractTests(unittest.TestCase):
    def setUp(self):
        self.analyzer = DatabricksDashboardAnalyzer()
        self.request = DashboardRequest.model_validate(request_data())
        self.deployment = Deployment.model_validate(config_data())
        self.policy = self.deployment.metrics['glucose_variability']

    def test_post_without_verified_workflow_returns_four_null_results(self):
        with patch.dict(os.environ, {'DASHBOARD_MODEL_CONFIG': ''}), TestClient(app) as client:
            response = client.post('/make_prediction', json=request_data(False))
        self.assertEqual(200, response.status_code)
        self.assertEqual(set(METRICS), set(response.json()['results']))
        for result in response.json()['results'].values():
            self.assertEqual('unavailable', result['availability'])
            for key in ('value', 'confidence', 'risk_probability', 'bar_position', 'threshold_version'):
                self.assertIsNone(result[key])

    def test_legacy_get_route_and_parameters_remain(self):
        schema = app.openapi()['paths']['/make_prediction']
        self.assertIn('post', schema)
        self.assertEqual({'heartbeat', 'glucose', 'Interbeat_interval', 'ACC'}, {p['name'] for p in schema['get']['parameters']})

    def test_valid_zero_eda_and_timestamp_units_are_preserved(self):
        self.assertEqual(0, self.request.sensors['eda'].readings[0].values[0])
        self.assertEqual('uS', self.request.sensors['eda'].unit)
        self.assertEqual(timezone.utc, self.request.sensors['eda'].readings[0].timestamp.tzinfo)

    def test_invalid_coverage_nan_order_and_outside_window_are_rejected(self):
        for mutation in ('count', 'nan', 'duplicate', 'outside', 'interval'):
            data = request_data()
            series = data['sensors']['eda']
            if mutation == 'count': series['coverage']['sample_count'] = 2
            if mutation == 'nan': series['readings'][2]['values'][0] = float('nan')
            if mutation == 'duplicate': series['readings'][2]['timestamp'] = series['readings'][1]['timestamp']
            if mutation == 'outside': series['readings'][2]['timestamp'] = data['source_window']['end']
            if mutation == 'interval': data['interval'] = 2
            with self.subTest(mutation=mutation), self.assertRaises(ValidationError):
                DashboardRequest.model_validate(data)

    def test_bad_payload_returns_422(self):
        with TestClient(app) as client:
            self.assertEqual(422, client.post('/make_prediction', json={'heartbeat': 70}).status_code)

    def test_unverified_clock_is_unavailable(self):
        reason = self.analyzer.missing_inputs(DashboardRequest.model_validate(request_data(False)), 'glucose_variability', self.policy)
        self.assertIn('alignment', reason)

    def test_short_source_is_not_extended_for_analysis(self):
        data = request_data()
        series = data['sensors']['glucose']
        series['readings'] = series['readings'][:100]
        series['coverage']['sample_count'] = 100
        series['coverage']['last_timestamp'] = series['readings'][-1]['timestamp']
        reason = self.analyzer.missing_inputs(DashboardRequest.model_validate(data), 'glucose_variability', self.policy)
        self.assertIn('ends too early', reason)

    def test_missing_temperature_does_not_block_a_supported_glucose_metric(self):
        policy = policy_data()
        policy['required_sensors'] = {name: {'unit': unit, 'min_samples': 2, 'max_gap_seconds': 2} for name, unit in
            [('hr','bpm'),('eda','uS'),('acc','device_counts'),('temp','degC'),('ibi','s'),('glucose','mg/dL')]}
        data = request_data()
        acc = copy.deepcopy(data['sensors']['hr']);acc['unit']='device_counts'
        for reading in acc['readings']: reading['values'] = [0, 0, 0]
        data['sensors']['acc'] = acc
        request = DashboardRequest.model_validate(data)
        self.assertIn('TEMP', self.analyzer.missing_inputs(request, 'prediabetes_risk', MetricPolicy.model_validate(policy)))
        self.assertIsNone(self.analyzer.missing_inputs(request, 'glucose_variability', self.policy))

    def test_mean_ibi_or_hr_only_cannot_satisfy_hrv_or_hr_eda(self):
        for key in ('hrv', 'hr_eda'):
            self.assertIn('incomplete', self.analyzer.missing_inputs(self.request, key, self.policy))

    def test_units_and_meal_requirements_are_checked(self):
        policy = self.policy.model_copy(update={'requires_meals': True})
        self.assertIn('meal', self.analyzer.missing_inputs(self.request, 'glucose_variability', policy))
        data = request_data();data['sensors']['glucose']['unit'] = 'unknown'
        self.assertIn('units', self.analyzer.missing_inputs(DashboardRequest.model_validate(data), 'glucose_variability', self.policy))

    def test_threshold_boundaries_and_null_confidence(self):
        for value, category, position in [(9.999,'low',0.2),(10,'moderate',0.6),(19.999,'moderate',0.6),(20,'high',0.9)]:
            result = self.analyzer.validate_output(output(value,category,position), self.policy, self.deployment, 'window-1')
            self.assertEqual(category, result.risk_category)
            self.assertIsNone(result.confidence)
            self.assertEqual(0.3, result.risk_probability)

    def test_category_bar_threshold_and_model_mismatches_are_rejected(self):
        for field,value in [('risk_category','low'),('bar_position',0.9),('threshold_version','invented'),('model_version','8'),('window_id','old-window'),('unit','other'),('value','10'),('confidence',True)]:
            data = output();data[field]=value
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.analyzer.validate_output(data,self.policy,self.deployment,'window-1')

    def test_confidence_requires_model_validation_and_probability_is_independent(self):
        with self.assertRaises(ValueError):
            self.analyzer.validate_output(output(confidence=.9),self.policy,self.deployment,'window-1')
        policy = self.policy.model_copy(update={'confidence_validated': True})
        result = self.analyzer.validate_output(output(confidence=.9),policy,self.deployment,'window-1')
        self.assertEqual(.9,result.confidence)
        self.assertEqual(.3,result.risk_probability)
        with self.assertRaises(ValueError):
            self.analyzer.validate_output(output(confidence=1.1),policy,self.deployment,'window-1')

    def test_postmeal_interpretation_requires_model_support(self):
        data=output();data['supports_postmeal_spikes']=True
        with self.assertRaises(ValueError):
            self.analyzer.validate_output(data,self.policy,self.deployment,'window-1')

    def test_unavailable_results_never_contain_predictions(self):
        with self.assertRaises(ValidationError):
            MetricResult(availability='unavailable',value=1)

    def test_partial_results_validate_independently(self):
        config = config_data()
        config['metrics']['hrv'] = policy_data()
        config['metrics']['hrv']['required_sensors'] = {'ibi': {'unit':'s','min_samples':2,'max_gap_seconds':2}}
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'model.json';path.write_text(json.dumps(config))
            with patch.dict(os.environ, {'DASHBOARD_MODEL_CONFIG': str(path)}), patch.object(self.analyzer, 'invoke', return_value={'glucose_variability':output(),'hrv':{'value':'raw LLM text'}}):
                response=self.analyzer.analyze(self.request)
        self.assertEqual('available',response.results['glucose_variability'].availability)
        self.assertEqual('unavailable',response.results['hrv'].availability)
        self.assertEqual('unavailable',response.results['prediabetes_risk'].availability)

    def test_inference_failure_and_invalid_config_are_unavailable(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'config.json';path.write_text(json.dumps(config_data()))
            with patch.dict(os.environ, {'DASHBOARD_MODEL_CONFIG':str(path)}), patch.object(self.analyzer,'invoke',side_effect=TimeoutError('secret host and body')):
                result=self.analyzer.analyze(self.request)
            self.assertTrue(all(r.availability=='unavailable' for r in result.results.values()))
            self.assertNotIn('secret',result.model_dump_json())
            path.write_text('{}')
            with patch.dict(os.environ, {'DASHBOARD_MODEL_CONFIG':str(path)}):
                self.assertTrue(all(r.availability=='unavailable' for r in self.analyzer.analyze(self.request).results.values()))

    def test_endpoint_verification_and_full_supported_inputs(self):
        sent=[]
        def handler(request):
            sent.append(request)
            if request.method=='GET':
                return httpx.Response(200,json={'state':{'ready':'READY'},'config':{'served_entities':[{'entity_name':self.deployment.entity_name,'entity_version':'7'}]}})
            return httpx.Response(200,json={'predictions':[{'results':{'glucose_variability':output()}}]})
        factory=httpx.Client
        with patch.dict(os.environ,{'DATABRICKS_HOST':'https://test.invalid','DATABRICKS_TOKEN':'fixture'}), patch('Api.dashboard_analysis.httpx.Client',side_effect=lambda **kwargs: factory(transport=httpx.MockTransport(handler),**kwargs)):
            results=self.analyzer.invoke(self.deployment,self.request)
        self.assertEqual('moderate',results['glucose_variability']['risk_category'])
        posted=json.loads(sent[-1].content)['inputs'][0]
        self.assertEqual(self.request.model_dump(mode='json')['sensors'],posted['sensors'])
        self.assertEqual({'validated_feature':3.5},posted['features'])
        self.assertNotIn('cumulative_replay_averages',posted)

    def test_foundation_models_wrong_versions_and_routed_endpoints_are_not_used(self):
        factory=httpx.Client
        entity={'entity_name':self.deployment.entity_name,'entity_version':'7'}
        for entities in [[dict(entity,type='FOUNDATION_MODEL')],[dict(entity,entity_version='8')],[entity,entity]]:
            sent=[]
            def handler(request):
                sent.append(request)
                return httpx.Response(200,json={'state':{'ready':'READY'},'config':{'served_entities':entities}})
            with patch.dict(os.environ,{'DATABRICKS_HOST':'https://test.invalid','DATABRICKS_TOKEN':'fixture'}), patch('Api.dashboard_analysis.httpx.Client',side_effect=lambda **kwargs:factory(transport=httpx.MockTransport(handler),**kwargs)), self.assertRaises(ValueError):
                self.analyzer.invoke(self.deployment,self.request)
            self.assertEqual(1,len(sent))

    def test_restorative_range_is_optional_and_model_configured(self):
        data=output();data['reference_range']=dict(data['reference_range'],restorative_bar_start=.25,restorative_bar_end=.5)
        policy_data_=policy_data();policy_data_['reference_range']=data['reference_range']
        result=self.analyzer.validate_output(data,MetricPolicy.model_validate(policy_data_),self.deployment,'window-1')
        self.assertEqual(.25,result.reference_range.restorative_bar_start)
        data['reference_range']['restorative_bar_end']=.2
        with self.assertRaises(ValidationError):MetricResult.model_validate(data)

if __name__ == '__main__':
    unittest.main()
