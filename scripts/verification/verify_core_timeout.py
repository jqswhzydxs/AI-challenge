# Read-only Agent request during a temporary Core process pause in the isolated demo.
import datetime as dt,json,time
import verify as v
def main():
    token=v.data('/api/auth/login',{'username':'admin','password':'123456'})['token']
    before=v.sql('SELECT COUNT(*) FROM agent_run','industrial_agent')
    plan=v.sql('SELECT id FROM energy_plan WHERE deleted=0 ORDER BY id DESC LIMIT 1')
    v.command(['pause','core'])
    try:
        start=time.monotonic();status,response=v.http('/api/agent/energy-plan/'+plan+'/diagnose',{},token);elapsed=time.monotonic()-start
        assert status==503 and elapsed<10,(status,elapsed)
        assert 'data' not in response and 'riskLevel' not in response
    finally:v.command(['unpause','core'])
    assert v.sql('SELECT COUNT(*) FROM agent_run','industrial_agent')==before
    v.data('/api/production/schedule/history?page=1&size=5',token=token)
    # A completed business import wins over a later cancel request.
    task_id=v.sql("SELECT id FROM algorithm_task WHERE execution_mode='REMOTE' AND status='SUCCESS' ORDER BY id DESC LIMIT 1")
    _,cancel=v.http('/api/tasks/'+task_id+'/cancel',{},token)
    assert cancel['code']==409
    v.passed('A06-core-timeout',{'httpStatus':status,'elapsedSeconds':round(elapsed,2),'analysis':'no diagnosis without authorization; no fabricated low risk; no model call'})
    v.passed('T14-already-applied-cancel-rejected',{'taskId':task_id,'businessCode':409})
if __name__=='__main__':
    start=time.monotonic();success=False
    try:main();success=True
    except Exception as error:v.records.append({'case':'execution','status':'FAIL','error':str(error)});print(str(error),flush=True)
    finally:
        report={'executedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'success':success,'elapsedSeconds':round(time.monotonic()-start,2),'realModelEvaluated':False,'records':v.records}
        output=v.ROOT/'docs/microservices/verification-runs'/(dt.datetime.now().strftime('%Y%m%d-%H%M%S-%f')+'-core-timeout.json')
        output.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print('Saved report: '+str(output),flush=True)
    raise SystemExit(0 if success else 1)
