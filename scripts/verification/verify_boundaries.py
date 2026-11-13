# Deterministic commit/ACK, fencing, manifest, cancellation-order and reservation-clock checks.
import datetime as dt,json,os,subprocess,time,uuid,statistics
import verify as v
import verify_durability as d

def ready_core():return v.wait(lambda:v.http('/api/auth/login',{'username':'admin','password':'123456'})[1].get('code')==200,120)
def hold_publish():v.sql("CREATE TRIGGER verification_hold_publish BEFORE INSERT ON core_outbox FOR EACH ROW SET NEW.next_send=TIMESTAMPADD(DAY,1,UTC_TIMESTAMP(3))")
def release_publish():
    v.sql('DROP TRIGGER verification_hold_publish');v.sql("UPDATE core_outbox SET next_send=UTC_TIMESTAMP(3),lease_until=NULL WHERE state='PENDING'")
def main(skip_completed=False):
    token=v.data('/api/auth/login',{'username':'admin','password':'123456'})['token']
    if not skip_completed:
        d.configure(delay='6',scale=1);ready_core()
        hold_publish()
        try:
            current=v.submit(token,d.available_date())
            v.wait(lambda:v.sql(f"SELECT COUNT(*) FROM core_outbox WHERE request_id='{current['requestId']}'")=='1')
            assert v.sql(f"SELECT state FROM core_outbox WHERE request_id='{current['requestId']}'")=='PENDING'
            v.command(['kill','-s','SIGKILL','core']);v.command(['start','core']);ready_core()
        finally:release_publish()
        v.finished(token,current['taskId']);v.assert_unique(current['requestId'])
        v.passed('T03-exit-after-commit-before-publish','Core killed after durable commit while outbox delivery was held; restart published the original request once.')
    
        os.environ['VERIFICATION_AFTER_COMMAND_COMMIT_DELAY_MS']='30000'
        d.configure(delay='6',scale=1);ready_core()
        v.sql("CREATE TRIGGER verification_hold_claim BEFORE INSERT ON compute_job FOR EACH ROW SET NEW.next_run=TIMESTAMPADD(DAY,1,UTC_TIMESTAMP(3))",'industrial_algorithm')
        try:
            accepted=v.submit(token,d.available_date());d.wait_state(accepted['requestId'],{'ACCEPTED'})
            unacked=v.command(['exec','-T','rabbitmq','rabbitmqctl','list_queues','name','messages_unacknowledged','-q']).stdout
            assert any(line.split()==['industrial.algorithm.commands.v1','1'] for line in unacked.splitlines()),unacked
            v.command(['kill','-s','SIGKILL','algorithm'])
            os.environ['VERIFICATION_AFTER_COMMAND_COMMIT_DELAY_MS']='0';d.configure(delay='6')
        finally:v.sql('DROP TRIGGER verification_hold_claim','industrial_algorithm')
        v.sql(f"UPDATE compute_job SET next_run=UTC_TIMESTAMP(3) WHERE request_id='{accepted['requestId']}'",'industrial_algorithm')
        v.finished(token,accepted['taskId']);v.assert_unique(accepted['requestId'])
        assert v.sql(f"SELECT attempt FROM compute_job WHERE request_id='{accepted['requestId']}'",'industrial_algorithm')=='1'
        v.passed('T05-exact-commit-before-ack','RabbitMQ showed one unacknowledged command after DB commit. SIGKILL caused re-delivery; original inbox/job survived and only one attempt executed.')
    
    os.environ['VERIFICATION_BEFORE_TERMINAL_DELAY_MS']='45000'
    d.configure(delay='1',scale=2);ready_core()
    fenced=v.submit(token,d.available_date());d.wait_state(fenced['requestId'],{'RUNNING'})
    started=time.monotonic()
    try:
        v.finished(token,fenced['taskId'])
        assert int(v.sql(f"SELECT attempt FROM compute_job WHERE request_id='{fenced['requestId']}'",'industrial_algorithm'))>=2
        v.wait(lambda:v.sql(f"SELECT COUNT(*) FROM compute_attempt a JOIN compute_job j ON a.job_id=j.job_id WHERE j.request_id='{fenced['requestId']}' AND a.attempt=1 AND a.state='STOPPED'",'industrial_algorithm')=='1',90)
        v.assert_unique(fenced['requestId'])
        assert v.sql(f"SELECT COUNT(*) FROM algorithm_outbox WHERE request_id='{fenced['requestId']}' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.eventType'))='AlgorithmSucceeded.v1'",'industrial_algorithm')=='1'
        v.passed('T08-live-old-attempt-fencing',{'requestId':fenced['requestId'],'elapsedSeconds':round(time.monotonic()-started,2),'oldOutput':'persisted before delayed CAS; old attempt cannot commit terminal event','validSuccessEvents':1})
    finally:
        os.environ['VERIFICATION_BEFORE_TERMINAL_DELAY_MS']='0';d.configure(delay='1',scale=2);ready_core()

    # Tamper a frozen CSV while execution is durably held.
    v.sql("CREATE TRIGGER verification_hold_hash BEFORE INSERT ON compute_job FOR EACH ROW SET NEW.next_run=TIMESTAMPADD(DAY,1,UTC_TIMESTAMP(3))",'industrial_algorithm')
    try:
        corrupted=v.submit(token,d.available_date());d.wait_state(corrupted['requestId'],{'ACCEPTED'})
        event=json.loads(v.sql(f"SELECT request_payload FROM compute_job WHERE request_id='{corrupted['requestId']}'",'industrial_algorithm'))
        script="import json;from pathlib import Path;m=json.loads(Path('/artifacts/"+event['inputRef']+"').read_text());p=Path('/artifacts/'+m['energyRef']);p.write_bytes(p.read_bytes()+b'corrupted')"
        v.command(['exec','-T','algorithm','python','-c',script])
    finally:v.sql('DROP TRIGGER verification_hold_hash','industrial_algorithm')
    v.sql(f"UPDATE compute_job SET next_run=UTC_TIMESTAMP(3) WHERE request_id='{corrupted['requestId']}'",'industrial_algorithm')
    d.failure(token,corrupted,'ARTIFACT_HASH_MISMATCH')
    v.passed('T09-real-input-hash-tamper','Changed frozen CSV rejected before Python execution; no business plan.')

    # Reverse command arrival while preserving the Core-authorized cancellation intent.
    hold_publish()
    try:
        reversed_task=v.submit(token,d.available_date())
        v.wait(lambda:v.sql(f"SELECT COUNT(*) FROM core_outbox WHERE request_id='{reversed_task['requestId']}'")=='1')
        v.data('/api/tasks/'+str(reversed_task['taskId'])+'/cancel',{},token)
        events=[json.loads(line) for line in v.sql(f"SELECT payload FROM core_outbox WHERE request_id='{reversed_task['requestId']}' ORDER BY created_at").splitlines()]
        cancel=next(item for item in events if item['eventType']=='AlgorithmCancelRequested.v1')
        request=next(item for item in events if item['eventType']=='AlgorithmRequested.v1')
        v.publish(cancel,'commands');d.wait_state(reversed_task['requestId'],{'CANCELED'});v.publish(request,'commands')
    finally:release_publish()
    assert v.finished(token,reversed_task['taskId'])['status']=='CANCELED'
    assert v.sql(f"SELECT attempt FROM compute_job WHERE request_id='{reversed_task['requestId']}'",'industrial_algorithm')=='0'
    v.passed('T14-cancel-before-command','Cancellation tombstone arrived first; delayed request never started or applied.')

    reservation_checks(token)

def reservation_checks(token):
    # Core-only verification clock moves across the reservation deadline; identity and hashes are unchanged.
    date=d.available_date();business=dt.date.fromisoformat(date)
    os.environ['VERIFICATION_BUSINESS_INSTANT']=dt.datetime.combine(business-dt.timedelta(days=1),dt.time(12),dt.timezone(dt.timedelta(hours=8))).astimezone(dt.timezone.utc).isoformat()
    d.configure(delay='1');ready_core()
    try:
        key=str(uuid.uuid4());reservation=v.submit(token,date,key)
        assert v.sql(f"SELECT state FROM schedule_submission WHERE request_id='{reservation['requestId']}'")=='RESERVED'
        time.sleep(2)
        assert str(v.submit(token,date,key)['taskId'])==str(reservation['taskId'])
        os.environ['VERIFICATION_BUSINESS_INSTANT']=dt.datetime.combine(business,dt.time(0),dt.timezone(dt.timedelta(hours=8))).astimezone(dt.timezone.utc).isoformat()
        d.configure(delay='1');ready_core();v.finished(token,reservation['taskId']);v.assert_unique(reservation['requestId'])
        v.passed('T15-reservation-due-clock','Crossed due time using verification-only Clock; repeated scans reused original task/request and produced one plan.')
    finally:os.environ['VERIFICATION_BUSINESS_INSTANT']='';d.configure(delay='6');ready_core()

    # Ordinary query timing while an independent Python process is active.
    busy=v.submit(token,d.available_date());d.wait_state(busy['requestId'],{'RUNNING'})
    samples=[]
    for _ in range(20):
        start=time.perf_counter();v.data('/api/production/schedule/history?page=1&size=5',token=token);samples.append((time.perf_counter()-start)*1000)
    v.finished(token,busy['taskId'])
    v.passed('query-during-slow-python',{'samples':20,'p95Ms':round(sorted(samples)[18],2),'medianMs':round(statistics.median(samples),2),'scope':'same-machine verification process fixture, no performance improvement claim'})

if __name__=='__main__':
    started=time.monotonic();success=False
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('--from-fencing',action='store_true');parser.add_argument('--reservation-only',action='store_true');args=parser.parse_args()
    try:
        ready_core()
        if args.reservation_only:reservation_checks(v.data('/api/auth/login',{'username':'admin','password':'123456'})['token'])
        else:main(args.from_fencing)
        success=True
    except Exception as exception:v.records.append({'case':'execution','status':'FAIL','error':str(exception)});print(str(exception),flush=True)
    finally:
        report={'executedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'success':success,'elapsedSeconds':round(time.monotonic()-started,2),'profile':'verification; controlled ACK delay and Clock','records':v.records}
        folder=v.ROOT/'docs/microservices/verification-runs';folder.mkdir(parents=True,exist_ok=True)
        path=folder/(dt.datetime.now().strftime('%Y%m%d-%H%M%S-%f')+'-boundaries.json');path.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print('Saved report: '+str(path),flush=True)
    raise SystemExit(0 if success else 1)
