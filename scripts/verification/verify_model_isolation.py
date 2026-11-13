# This is a protocol/time-budget fixture, never a real-model accuracy evaluation.
import concurrent.futures,datetime as dt,json,statistics,time,subprocess
import verify as v
BASE=list(v.COMPOSE)
def ready():v.wait(lambda:v.http('/api/auth/login',{'username':'admin','password':'123456'})[1].get('code')==200,120)
def main():
    ready();token=v.data('/api/auth/login',{'username':'admin','password':'123456'})['token']
    plan=v.sql('SELECT id FROM energy_plan WHERE deleted=0 ORDER BY id DESC LIMIT 1')
    assert plan
    v.COMPOSE+=['-f',str(v.ROOT/'deploy/compose.model-verification.yaml')]
    try:
        v.command(['up','-d','--no-deps','model-fixture','agent'])
        v.wait(lambda:'healthy'==next((json.loads(line).get('Health') for line in v.command(['ps','--format','json']).stdout.splitlines() if json.loads(line).get('Service')=='agent'),None),120)
        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
            started=time.monotonic();analysis=pool.submit(v.data,'/api/agent/report/daily',{'businessDate':'2026-07-17'},token)
            reader="import json,urllib.request;print(json.load(urllib.request.urlopen('http://model-fixture:8000/stats'))['calls'])"
            v.wait(lambda:v.command(['exec','-T','algorithm','python','-c',reader]).stdout.strip()=='1')
            samples=[]
            for _ in range(20):
                start=time.perf_counter();v.data('/api/production/schedule/history?page=1&size=5',token=token);samples.append((time.perf_counter()-start)*1000)
            stats=v.command(['ps','-q']).stdout.splitlines()
            sample=subprocess.run(['docker','stats','--no-stream','--format','{{json .}}',*stats],capture_output=True,text=True,check=True)
            resources=[json.loads(line) for line in sample.stdout.splitlines()]
            resource=v.command(['exec','-T','algorithm','python','-c',reader]).stdout.strip()
            response=analysis.result(timeout=40);elapsed=time.monotonic()-started
        assert response['modelAttempted'] and not response['aiEnabled'] and response['source']=='RULE_FALLBACK_AFTER_AI_ERROR'
        assert elapsed<35,elapsed
        v.passed('A06-S15-slow-model-protocol-fixture',{'runId':response['runId'],'calls':int(resource),'elapsedSeconds':round(elapsed,2),'querySamples':20,'queryP95Ms':round(sorted(samples)[18],2),'queryMedianMs':round(statistics.median(samples),2),'source':response['source'],'realModel':False,'tokens':None,'resourceSample':resources})
    finally:
        v.COMPOSE[:]=BASE
        v.command(['up','-d','--no-deps','agent'])
        v.wait(lambda:'healthy'==next((json.loads(line).get('Health') for line in v.command(['ps','--format','json']).stdout.splitlines() if json.loads(line).get('Service')=='agent'),None),120)
        # Stop/remove only the explicitly named disposable fixture, preserving all data volumes.
        v.COMPOSE+=['-f',str(v.ROOT/'deploy/compose.model-verification.yaml')]
        v.command(['stop','model-fixture']);v.command(['rm','-f','model-fixture'])
        v.COMPOSE[:]=BASE
    v.passed('rule-mode-restored','Production Agent restored with no external model provider; fixture removed.')
if __name__=='__main__':
    start=time.monotonic();success=False
    try:main();success=True
    except Exception as error:v.records.append({'case':'execution','status':'FAIL','error':str(error)});print(str(error),flush=True)
    finally:
        report={'executedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'success':success,'elapsedSeconds':round(time.monotonic()-start,2),'realModelEvaluated':False,'fixture':'local HTTP model protocol delay','records':v.records}
        output=v.ROOT/'docs/microservices/verification-runs'/(dt.datetime.now().strftime('%Y%m%d-%H%M%S-%f')+'-model-isolation.json')
        output.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print('Saved report: '+str(output),flush=True)
    raise SystemExit(0 if success else 1)
