# Isolated Compose acceptance checks. Never connects to an existing host database.
# Run after: docker compose --env-file deploy/.env -f deploy/compose.yaml up -d --wait
from __future__ import annotations
import argparse,concurrent.futures,datetime as dt,json,os,pathlib,statistics,subprocess,time,urllib.request,urllib.error,uuid

ROOT=pathlib.Path(__file__).resolve().parents[2]
COMPOSE=['docker','compose','--env-file',str(ROOT/'deploy/.env'),'-f',str(ROOT/'deploy/compose.yaml')]
BASE='http://127.0.0.1:18080'
records=[]
def command(args,stdin=None,check=True):
    result=subprocess.run(COMPOSE+args,input=stdin,text=True,encoding='utf-8',capture_output=True,cwd=ROOT)
    if check and result.returncode:raise RuntimeError('Compose operation failed: '+result.stderr[-1200:])
    return result
def sql(query,schema='challenge_cup_energy'):
    assert schema in ('challenge_cup_energy','industrial_algorithm','industrial_agent')
    result=command(['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names '+schema],query)
    return result.stdout.strip()
def http(path,body=None,token=None,key=None,headers=None):
    request_headers={'Content-Type':'application/json',**(headers or {})}
    if token:request_headers['Authorization']='Bearer '+token
    if key:request_headers['Idempotency-Key']=key
    req=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers=request_headers)
    try:
        with urllib.request.urlopen(req,timeout=40) as response:return response.status,json.load(response)
    except urllib.error.HTTPError as error:
        try:return error.code,json.load(error)
        except ValueError:return error.code,{}
def data(path,body=None,token=None,key=None):
    status,value=http(path,body,token,key)
    if status!=200 or value.get('code')!=200:raise AssertionError(f'API {path}: HTTP {status}, business {value.get("code")}, {value.get("message")}')
    return value['data']
def wait(predicate,timeout=90):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        value=predicate()
        if value:return value
        time.sleep(.5)
    raise AssertionError('Timed out waiting for durable state')
def passed(name,details):
    records.append({'case':name,'status':'PASS','details':details});print(name+': PASS',flush=True)
def task(token,task_id):return data('/api/tasks/'+str(task_id),token=token)
def finished(token,task_id):
    def check():
        current=task(token,task_id)
        if current['status']=='FAILED':raise AssertionError('Task failed: '+str(current.get('errorMessage')))
        return current if current['status'] in ('SUCCESS','CANCELED') else None
    return wait(check,120)
def submit(token,date,key=None,quantity=6):return data('/api/production/schedules/generate-from-collected-data',{'scheduleDate':date,'plannedQuantity':quantity},token,key or str(uuid.uuid4()))
def seed():
    # Fixtures are appended lazily for each allocated date; no upsert of historical samples.
    return None

def available_date():
    from verification_fixtures import allocate
    return allocate(sql)

def assert_unique(request_id):
    assert sql(f"SELECT COUNT(*) FROM algorithm_task WHERE request_id='{request_id}'")=='1'
    assert sql(f"SELECT COUNT(*) FROM production_schedule_plan WHERE source_request_id='{request_id}'")=='1'
    assert sql(f"SELECT COUNT(*) FROM production_schedule_detail d JOIN production_schedule_plan p ON p.id=d.schedule_id WHERE p.source_request_id='{request_id}'")=='24'
    assert sql(f"SELECT COUNT(*) FROM compute_job WHERE request_id='{request_id}'",'industrial_algorithm')=='1'
def publish(event,routing):
    # Management API is internal-only. Credentials remain inside the container environment.
    script='''import sys,json,os,base64,urllib.request
event=json.load(sys.stdin)
payload={"properties":{"delivery_mode":2},"routing_key":"ROUTING","payload":json.dumps(event),"payload_encoding":"string"}
auth=base64.b64encode((os.environ['MQ_USERNAME']+':'+os.environ['MQ_PASSWORD']).encode()).decode()
req=urllib.request.Request('http://rabbitmq:15672/api/exchanges/%2F/industrial.algorithm.v1/publish',data=json.dumps(payload).encode(),headers={'Authorization':'Basic '+auth,'Content-Type':'application/json'})
assert json.load(urllib.request.urlopen(req))['routed']
'''.replace('ROUTING',routing)
    command(['exec','-T','algorithm','python','-c',script],json.dumps(event))

def main(faults=False):
    assert json.loads(command(['config','--format','json']).stdout)['name']=='challenge-microservices'
    _,admin_login=http('/api/auth/login',{'username':'admin','password':'123456'})
    token=admin_login['data']['token']
    user=data('/api/auth/login',{'username':'energy','password':'123456'})['token']
    seed()
    passed('S01-login-and-fixed-fixtures','Isolated MySQL; explicitly synthetic 15-minute energy data; JWT is not saved in reports.')
    date=available_date();key='verification-'+str(uuid.uuid4())
    first=submit(token,date,key)
    repeat=submit(token,date,key)
    assert str(first['taskId'])==str(repeat['taskId'])
    status,conflict=http('/api/production/schedules/generate-from-collected-data',{'scheduleDate':date,'plannedQuantity':7},token,key)
    assert conflict.get('code')==409
    passed('T01-idempotency',{'taskId':first['taskId'],'changedPayloadCode':conflict['code']})
    completed=finished(token,first['taskId']);assert completed['status']=='SUCCESS' and completed['applicationStatus']=='APPLIED'
    assert_unique(completed['requestId'])
    wait(lambda:task(token,first['taskId']).get('derivationStatus')=='COMPLETE')
    passed('normal-remote-closed-loop',{'taskId':first['taskId'],'requestId':completed['requestId'],'resultId':completed['resultId'],'detailCount':24})
    concurrent_date=available_date()
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        outcomes=list(executor.map(lambda key:http('/api/production/schedules/generate-from-collected-data',{'scheduleDate':concurrent_date,'plannedQuantity':6},token,key),[str(uuid.uuid4()),str(uuid.uuid4())]))
    assert sorted(value.get('code') for _,value in outcomes)==[200,409]
    second=next(value['data'] for _,value in outcomes if value['code']==200);finished(token,second['taskId'])
    passed('T02-same-date-concurrency','Exactly one accepted request; competing key received 409.')
    output=json.loads(sql(f"SELECT payload FROM algorithm_outbox WHERE request_id='{completed['requestId']}' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.eventType'))='AlgorithmSucceeded.v1' LIMIT 1",'industrial_algorithm'))
    publish(output,'results');reissued={**output,'eventId':str(uuid.uuid4()),'eventSeq':output['eventSeq']+10};publish(reissued,'results')
    late={**output,'eventId':str(uuid.uuid4()),'eventType':'AlgorithmRunning.v1','eventSeq':output['eventSeq']+20};publish(late,'results')
    time.sleep(2);assert task(token,first['taskId'])['status']=='SUCCESS';assert_unique(completed['requestId'])
    passed('T12-T13-duplicate-and-late-events','Same and new event IDs did not duplicate business application or regress SUCCESS.')
    schedule_id=str(completed['resultId']);energy_id=sql(f'SELECT id FROM energy_plan WHERE source_schedule_id={schedule_id} LIMIT 1')
    diagnosis=data('/api/agent/energy-plan/'+energy_id+'/diagnose',{},token)
    assert diagnosis['runId'] and diagnosis['analysisStatus']=='PARTIAL' and diagnosis['riskLevel']=='UNKNOWN' and not diagnosis['aiEnabled']
    saved=data('/api/agent/runs/'+diagnosis['runId'],token=token)
    assert saved['runId']==diagnosis['runId']
    assert sql(f"SELECT COUNT(*) FROM agent_tool_call WHERE run_id='{diagnosis['runId']}'",'industrial_agent')=='1'
    passed('agent-independent-rule-and-run',{'runId':diagnosis['runId'],'source':diagnosis['source'],'risk':diagnosis['riskLevel']})
    status,rejected=http('/api/agent/energy-plan/'+energy_id+'/diagnose',{},user,headers={'X-User-Id':'1000000000000000001','X-Role':'ADMIN'})
    assert status==403
    status,_=http('/api/agent/runs/'+diagnosis['runId'],token=user);assert status==404
    status,_=http('/internal/v1/auth/verify',{},token);assert status==404
    passed('A08-ownership-and-forged-headers','Different user rejected; run isolated; internal API absent from gateway routes.')
    denied=command(['exec','-T','mysql','sh','-c','MYSQL_PWD="$AGENT_DB_PASSWORD" mysql -uagent -Dindustrial_agent -e "SELECT id FROM challenge_cup_energy.sys_user LIMIT 1"'],check=False)
    assert denied.returncode!=0 and ('denied' in denied.stderr.lower())
    passed('D01-agent-database-isolation','Agent schema account cannot read Core sys_user.')
    missing=data('/api/agent/energy-plan/999999999999999999/diagnose',{},token)
    assert missing['riskLevel']=='UNKNOWN' and missing['analysisStatus']=='INSUFFICIENT_EVIDENCE'
    passed('A01-no-subject','Unknown object gives insufficient evidence, no default zero/LOW.')
    daily=data('/api/agent/report/daily',{'businessDate':'2026-07-17'},token)
    assert daily['runId'] and daily['riskLevel']=='UNKNOWN'
    passed('A07-dated-report',{'runId':daily['runId'],'date':'2026-07-17'})
    future=(dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).date()+dt.timedelta(days=2)).isoformat()
    used_dates=set(sql('SELECT schedule_date FROM schedule_generation_guard').splitlines())
    while future in used_dates:future=(dt.date.fromisoformat(future)+dt.timedelta(days=1)).isoformat()
    reservation=submit(token,future)
    canceled=data('/api/tasks/'+str(reservation['taskId'])+'/cancel',{},token)
    assert canceled['status']=='CANCELED'
    passed('T14-T15-reservation-cancel',{'taskId':reservation['taskId'],'requestId':reservation['requestId']})
    times=[]
    for _ in range(20):
        start=time.perf_counter();data('/api/production/schedule/history?page=1&size=5',token=token);times.append((time.perf_counter()-start)*1000)
    passed('query-measurement',{'samples':len(times),'p95Ms':round(sorted(times)[18],2),'medianMs':round(statistics.median(times),2),'scope':'idle post-deployment, no baseline improvement claim'})
    if faults:fault_tests(token)

def fault_tests(token):
    command(['stop','rabbitmq'])
    try:
        current=submit(token,available_date())
        wait(lambda:sql(f"SELECT COUNT(*) FROM core_outbox WHERE request_id='{current['requestId']}'")=='1')
        assert sql(f"SELECT state FROM core_outbox WHERE request_id='{current['requestId']}'")=='PENDING'
        data('/api/production/schedule/history?page=1&size=5',token=token)
    finally:command(['start','rabbitmq'])
    recovered=finished(token,current['taskId']);assert recovered['status']=='SUCCESS';assert_unique(current['requestId'])
    passed('T03-broker-outage-outbox','Accepted durable request survived broker outage and recovered; ordinary query remained available.')
    # A real MySQL trigger rejects the fourth hourly row; the import transaction must roll back entirely.
    sql("CREATE TRIGGER verification_fail_detail BEFORE INSERT ON production_schedule_detail FOR EACH ROW SET NEW.schedule_id=IF(NEW.hour_index=3,NULL,NEW.schedule_id)")
    try:
        broken=submit(token,available_date())
        wait(lambda:sql(f"SELECT state FROM algorithm_result_receipt WHERE request_id='{broken['requestId']}'")=='APPLY_RETRY')
        assert sql(f"SELECT COUNT(*) FROM production_schedule_plan WHERE source_request_id='{broken['requestId']}'")=='0'
        assert sql(f"SELECT result_id IS NULL FROM algorithm_task WHERE request_id='{broken['requestId']}'")=='1'
    finally:sql('DROP TRIGGER verification_fail_detail')
    recovered=finished(token,broken['taskId']);assert_unique(broken['requestId'])
    passed('T10-T11-application-rollback-and-retry','Real MySQL trigger rolled back the import; same receipt later applied once without recomputation.')
    command(['stop','agent'])
    try:data('/api/production/schedule/history?page=1&size=5',token=token)
    finally:command(['start','agent'])
    passed('agent-process-isolation','Stopping Agent did not prevent a normal Core query through gateway.')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--faults',action='store_true');parser.add_argument('--faults-only',action='store_true');args=parser.parse_args()
    start=time.monotonic();success=False
    try:
        if args.faults_only:
            token=data('/api/auth/login',{'username':'admin','password':'123456'})['token'];seed();fault_tests(token)
        else:main(args.faults)
        success=True
    except Exception as exception:
        records.append({'case':'execution','status':'FAIL','error':str(exception)});print(str(exception),flush=True)
    finally:
        report={'executedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'success':success,'elapsedSeconds':round(time.monotonic()-start,2),'realModelEvaluated':False,'records':records}
        directory=ROOT/'docs/microservices/verification-runs';directory.mkdir(parents=True,exist_ok=True)
        output=directory/(dt.datetime.now().strftime('%Y%m%d-%H%M%S-%f')+'-'+uuid.uuid4().hex[:8]+'.json');output.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print('Saved report: '+str(output),flush=True)
    raise SystemExit(0 if success else 1)

