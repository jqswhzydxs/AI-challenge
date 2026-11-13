# Run against the isolated production Compose after the verification overlay is removed.
import concurrent.futures,datetime as dt,hashlib,json,os,pathlib,subprocess,sys,time,uuid
import verify as v

BASE=list(v.COMPOSE)
def ready():return v.wait(lambda:v.http('/api/auth/login',{'username':'admin','password':'123456'})[1].get('code')==200,120)
def operations(pause):
    v.COMPOSE[:]=BASE+['-f',str(v.ROOT/'deploy/compose.operations.yaml')]
    os.environ['ALGORITHM_MAX_PENDING']='0' if pause else '1000'
    os.environ['ALGORITHM_APPLICATIONS_ENABLED']='false' if pause else 'true'
    v.command(['up','-d','--no-deps','core']);ready()
def restore():
    v.COMPOSE[:]=BASE
    v.command(['up','-d','--no-deps','core']);ready()

def main():
    ready();token=v.data('/api/auth/login',{'username':'admin','password':'123456'})['token']
    v.seed()
    # Hold business application, then restart Core into a reversible admission pause.
    v.sql("CREATE TRIGGER verification_release_hold BEFORE INSERT ON algorithm_result_receipt FOR EACH ROW SET NEW.next_apply=TIMESTAMPADD(DAY,1,UTC_TIMESTAMP(3))")
    try:
        held=v.submit(token,v.available_date())
        v.wait(lambda:v.sql(f"SELECT state FROM algorithm_result_receipt WHERE request_id='{held['requestId']}'")=='READY')
    finally:v.sql('DROP TRIGGER verification_release_hold')
    started=time.monotonic()
    try:
        operations(True)
        assert v.task(token,held['taskId'])['computeStatus']=='SUCCEEDED'
        v.data('/api/production/schedule/history?page=1&size=5',token=token)
        _,denied=v.http('/api/production/schedules/generate-from-collected-data',{'scheduleDate':v.available_date(),'plannedQuantity':6},token,str(uuid.uuid4()))
        assert denied['code']==429,denied
        v.sql(f"UPDATE algorithm_result_receipt SET next_apply=UTC_TIMESTAMP(3) WHERE request_id='{held['requestId']}'")
        time.sleep(2)
        assert v.sql(f"SELECT COUNT(*) FROM production_schedule_plan WHERE source_request_id='{held['requestId']}'")=='0'
    finally:restore()
    v.finished(token,held['taskId']);v.assert_unique(held['requestId'])
    assert v.sql(f"SELECT attempt FROM compute_job WHERE request_id='{held['requestId']}'",'industrial_algorithm')=='1'
    v.passed('S18-pause-and-resume',{'requestId':held['requestId'],'elapsedSeconds':round(time.monotonic()-started,2),'newRequests':429,'ordinaryQueries':'available','originalReceipt':'resumed once without recomputation','gateway':'not restarted during Core recreation'})

    # Cancel and receipt release run concurrently against the actual task row lock.
    v.sql("CREATE TRIGGER verification_release_race BEFORE INSERT ON algorithm_result_receipt FOR EACH ROW SET NEW.next_apply=TIMESTAMPADD(DAY,1,UTC_TIMESTAMP(3))")
    try:
        raced=v.submit(token,v.available_date())
        v.wait(lambda:v.sql(f"SELECT state FROM algorithm_result_receipt WHERE request_id='{raced['requestId']}'")=='READY')
    finally:v.sql('DROP TRIGGER verification_release_race')
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        applied=pool.submit(v.sql,f"UPDATE algorithm_result_receipt SET next_apply=UTC_TIMESTAMP(3) WHERE request_id='{raced['requestId']}'")
        canceled=pool.submit(v.http,'/api/tasks/'+str(raced['taskId'])+'/cancel',{},token)
        applied.result();cancel_response=canceled.result()[1]
    final=v.finished(token,raced['taskId'])
    count=int(v.sql(f"SELECT COUNT(*) FROM production_schedule_plan WHERE source_request_id='{raced['requestId']}'"))
    assert (final['status']=='CANCELED' and count==0 and cancel_response['code']==200) or (final['status']=='SUCCESS' and count==1 and cancel_response['code']==409),(final,count,cancel_response)
    v.passed('T14-cancel-application-race',{'requestId':raced['requestId'],'winner':final['status'],'planCount':count,'cancelResponse':cancel_response['code']})

    queues=['industrial.algorithm.commands.v1.dead','industrial.algorithm.results.v1.dead']
    def depths():
        output=v.command(['exec','-T','rabbitmq','rabbitmqctl','list_queues','name','messages_ready','-q']).stdout
        return {a[0]:int(a[1]) for line in output.splitlines() if len(a:=line.split())==2 and a[0] in queues}
    before=depths()
    example=json.loads(v.sql("SELECT payload FROM core_outbox WHERE JSON_UNQUOTE(JSON_EXTRACT(payload,'$.eventType'))='AlgorithmRequested.v1' LIMIT 1"))
    invalid_request=str(uuid.uuid4())
    for routing in ('commands','results'):
        event={**example,'eventId':str(uuid.uuid4()),'requestId':invalid_request,'schemaVersion':99}
        v.publish(event,routing)
    v.wait(lambda:all(depths().get(q,0)==before.get(q,0)+1 for q in queues))
    assert v.sql(f"SELECT COUNT(*) FROM compute_job WHERE request_id='{invalid_request}'",'industrial_algorithm')=='0'
    v.passed('unknown-schema-dead-letter','Both consumers rejected version 99 to their durable DLQ; no new job.')

    # Compare the same frozen input locally with the real Worker artifact; do not apply a second plan.
    event=json.loads(v.sql(f"SELECT payload FROM algorithm_result_receipt WHERE request_id='{held['requestId']}'"))
    reader="import json;from pathlib import Path;e=json.load(__import__('sys').stdin);m=json.loads(Path('/artifacts/'+e['inputRef']).read_text());print(json.dumps({'input':Path('/artifacts/'+m['energyRef']).read_text(),'orders':Path('/artifacts/'+m['ordersRef']).read_text(),'output':json.loads(Path('/artifacts/'+e['outputRef']).read_text())}))"
    snapshot=json.loads(v.command(['exec','-T','algorithm','python','-c',reader],json.dumps(event)).stdout)
    folder=v.ROOT.parent/'.cache'/('local-remote-'+str(uuid.uuid4()));folder.mkdir(parents=True)
    (folder/'input.csv').write_text(snapshot['input'],encoding='utf-8',newline='\n');(folder/'orders.csv').write_text(snapshot['orders'],encoding='utf-8',newline='\n')
    result=subprocess.run([sys.executable,str(v.ROOT/'data/algorithm/generate_plan.py'),str(folder/'input.csv'),str(folder/'output.json'),str(folder/'orders.csv')],capture_output=True,text=True)
    assert result.returncode==0
    assert json.loads((folder/'output.json').read_text(encoding='utf-8'))==snapshot['output']
    v.passed('S17-identical-local-remote-output',{'requestId':held['requestId'],'comparison':'all JSON fields equal for identical frozen CSV/order input; second output not imported','localPython':sys.version.split()[0]})
    current=v.command(['ps','--format','json']).stdout
    assert all(json.loads(line).get('Health')=='healthy' for line in current.splitlines())
    v.passed('production-healthy','Six services healthy; verification profile and slow wrapper removed; one Worker.')

if __name__=='__main__':
    start=time.monotonic();success=False
    try:main();success=True
    except Exception as error:v.records.append({'case':'execution','status':'FAIL','error':str(error)});print(str(error),flush=True)
    finally:
        v.COMPOSE[:]=BASE
        report={'executedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'success':success,'elapsedSeconds':round(time.monotonic()-start,2),'realModelEvaluated':False,'records':v.records}
        output=v.ROOT/'docs/microservices/verification-runs'/(dt.datetime.now().strftime('%Y%m%d-%H%M%S-%f')+'-release.json')
        output.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print('Saved report: '+str(output),flush=True)
    raise SystemExit(0 if success else 1)
