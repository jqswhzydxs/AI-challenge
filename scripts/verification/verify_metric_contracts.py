"""Read-only metric/evidence integration checks on the isolated Compose Gateway."""
from decimal import Decimal
import datetime as dt
import json
import uuid
import verify as v


def main():
    token=v.data('/api/auth/login',{'username':'admin','password':'123456'})['token']
    history=v.data('/api/energy/plans/history?pageNum=1&pageSize=30',token=token)['records']
    summary=next(p for p in history if p.get('evidenceSummary',{}).get('peakPowerKw') is not None)
    plan=v.data('/api/energy/plan/'+summary['planDate'],token=token)
    evidence=plan['evidenceSummary']
    total=Decimal(str(plan['totalEnergyCost']))
    electric=Decimal(str(plan['electricityCost']));steam=Decimal(str(plan['steamCost']))
    assert total==electric+steam
    for part,key in [(electric,'electricityCostSharePct'),(steam,'steamCostSharePct')]:
        if total>0:assert abs(Decimal(str(evidence[key]))-part/total*100)<Decimal('.0001')
    v.passed('M01-dynamic-cost-contributions',{'planId':plan['planId'],'date':plan['planDate'],'totalCost':str(total),'electricityCost':str(electric),'steamCost':str(steam),'steamSharePct':evidence.get('steamCostSharePct')})
    groups={}
    for detail in plan['details']:
        groups[detail['timestamp']]=groups.get(detail['timestamp'],Decimal(0))+Decimal(str(detail['electricityConsumption']))
    rates=[quantity*60/Decimal(plan['timeInterval']) for quantity in groups.values()]
    assert abs(Decimal(str(evidence['peakPowerKw']))-max(rates))<Decimal('.000001')
    assert abs(Decimal(str(evidence['avgPowerKw']))-sum(rates)/len(rates))<Decimal('.000001')
    assert plan['measurementType']=='DERIVED_FROM_PLAN' and plan['executionStatus']=='UNKNOWN'
    assert all(plan['optimizationResult'].get(k) is None for k in ('estimatedSavings','originalCost','peakShavingRate'))
    v.passed('M02-quantity-rate-and-planned-semantics',{'intervalMinutes':plan['timeInterval'],'peakPowerKw':evidence['peakPowerKw'],'averagePowerKw':evidence['avgPowerKw'],'measurementType':plan['measurementType'],'executionStatus':plan['executionStatus']})
    diagnosis=v.data('/api/agent/energy-plan/'+str(plan['planId'])+'/diagnose',{},token)
    saved=v.data('/api/agent/runs/'+diagnosis['runId'],token=token)
    assert diagnosis['rawAnswer']==saved['rawAnswer'] and diagnosis['evidence']==saved['evidence']
    snapshot=json.loads(diagnosis['rawAnswer'])['energyPlan']['data']
    assert Decimal(str(snapshot['totalEnergyCost']))==total
    assert snapshot['measurementType']=='DERIVED_FROM_PLAN'
    assert diagnosis['riskLevel']=='UNKNOWN' and diagnosis['analysisStatus']=='PARTIAL'
    assert not diagnosis['modelAttempted'] and not diagnosis['aiEnabled']
    assert v.sql(f"SELECT COUNT(*) FROM agent_tool_call WHERE run_id='{diagnosis['runId']}'",'industrial_agent')=='1'
    v.passed('M03-frozen-snapshot-and-conservative-rules',{'runId':diagnosis['runId'],'source':diagnosis['source'],'riskLevel':diagnosis['riskLevel'],'snapshotCallCount':1,'availableEquipmentCount':len(snapshot.get('availableEquipmentCapacities',[]))})
    capabilities=v.data('/api/agent/capabilities',token=token)
    assert capabilities['modelEnabled'] is False and capabilities['mode']=='RULE_ONLY'
    v.passed('M04-explicit-model-capabilities',{'modelEnabled':False,'mode':'RULE_ONLY','realModelEvaluated':False})


if __name__=='__main__':
    success=False
    try:main();success=True
    except Exception as error:
        v.records.append({'case':'execution','status':'FAIL','error':str(error)});print(str(error),flush=True)
    finally:
        report={'executedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'success':success,'realModelEvaluated':False,'records':v.records}
        directory=v.ROOT/'docs/microservices/verification-runs';directory.mkdir(parents=True,exist_ok=True)
        output=directory/(dt.datetime.now().strftime('%Y%m%d-%H%M%S-%f')+'-'+uuid.uuid4().hex[:8]+'-metrics.json')
        output.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print('Saved report: '+str(output),flush=True)
    raise SystemExit(0 if success else 1)
