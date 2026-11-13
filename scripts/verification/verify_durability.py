# Requires the explicit verification Compose overlay. Uses only challenge-microservices containers.
import datetime as dt,json,os,pathlib,subprocess,time,uuid,concurrent.futures
import verify as v

v.COMPOSE+=['-f',str(v.ROOT/'deploy/compose.verification.yaml')]
def available_date():
    return v.available_date()

def state(request):return v.sql(f"SELECT state FROM compute_job WHERE request_id='{request}'",'industrial_algorithm')
def wait_state(request,expected):return v.wait(lambda:state(request) in expected,120)
def configure(mode='normal',delay='6',timeout='180',scale=1):
    os.environ.update(VERIFICATION_MODE=mode,VERIFICATION_DELAY_SECONDS=delay,VERIFICATION_TIMEOUT_SECONDS=timeout)
    v.command(['up','-d','--no-deps','--scale','algorithm='+str(scale),'core','algorithm'])
    v.wait(lambda:len([json.loads(line) for line in v.command(['ps','--format','json']).stdout.splitlines() if line and json.loads(line).get('Service')=='algorithm' and json.loads(line).get('Health')=='healthy'])==scale,120)
    # Recreating Core can briefly leave Gateway pointing at a stopped endpoint.
    # Probe readiness via Gateway before submitting; never blindly retry a task POST.
    v.wait(lambda:v.http('/api/auth/login',{'username':'admin','password':'123456'})[1].get('code')==200,120)
def complete(token,current):return v.finished(token,current['taskId'])
def failure(token,current,code):
    value=v.wait(lambda:(row if (row:=v.task(token,current['taskId']))['status']=='FAILED' else None),120)
    assert value['errorMessage']==code,(value['errorMessage'],code)
    assert v.sql(f"SELECT COUNT(*) FROM production_schedule_plan WHERE source_request_id='{current['requestId']}'")=='0'
    return value
def main():
    assert json.loads(v.command(['config','--format','json']).stdout)['name']=='challenge-microservices'
    v.seed();token=v.data('/api/auth/login',{'username':'admin','password':'123456'})['token']
    configure()
    # Freeze a durable accepted job before scheduling it. Restart and redeliver the identical command.
    v.sql("CREATE TRIGGER verification_delay_claim BEFORE INSERT ON compute_job FOR EACH ROW SET NEW.next_run=TIMESTAMPADD(DAY,1,UTC_TIMESTAMP(3))",'industrial_algorithm')
    try:
        pending=v.submit(token,available_date());wait_state(pending['requestId'],{'ACCEPTED'})
        request=json.loads(v.sql(f"SELECT payload FROM core_outbox WHERE request_id='{pending['requestId']}' LIMIT 1"))
        v.command(['kill','-s','SIGKILL','algorithm']);v.command(['start','algorithm'])
        v.wait(lambda:v.command(['exec','-T','algorithm','python','-c',"import urllib.request;urllib.request.urlopen('http://localhost:8083/actuator/health/readiness')"],check=False).returncode==0)
        v.publish(request,'commands')
    finally:v.sql('DROP TRIGGER verification_delay_claim','industrial_algorithm')
    v.sql(f"UPDATE compute_job SET next_run=UTC_TIMESTAMP(3) WHERE request_id='{pending['requestId']}'",'industrial_algorithm')
    complete(token,pending);v.assert_unique(pending['requestId'])
    v.passed('T05-T06-durable-accept-and-redelivery','Accepted database job survived SIGKILL before execution; identical command re-delivery reused the job. Exact commit-before-ACK timing was not forced.')

    # Kill the process container while Python is sleeping. Two workers must recover one request.
    crashed=v.submit(token,available_date());wait_state(crashed['requestId'],{'RUNNING'})
    initial=v.sql(f"SELECT attempt FROM compute_job WHERE request_id='{crashed['requestId']}'",'industrial_algorithm')
    v.command(['kill','-s','SIGKILL','algorithm']);start=time.monotonic()
    configure(scale=2);complete(token,crashed);v.assert_unique(crashed['requestId'])
    attempt=int(v.sql(f"SELECT attempt FROM compute_job WHERE request_id='{crashed['requestId']}'",'industrial_algorithm'))
    assert attempt>int(initial)
    v.passed('T07-T08-two-worker-lease-recovery',{'requestId':crashed['requestId'],'attempt':attempt,'recoverySeconds':round(time.monotonic()-start,2),'workers':2})
    # Replay a command after broker acceptance but before a simulated SENT update.
    v.sql(f"UPDATE core_outbox SET state='PENDING',next_send=UTC_TIMESTAMP(3),lease_until=NULL WHERE request_id='{crashed['requestId']}'")
    time.sleep(2);v.assert_unique(crashed['requestId'])
    assert int(v.sql(f"SELECT attempt FROM compute_job WHERE request_id='{crashed['requestId']}'",'industrial_algorithm'))==attempt
    v.passed('T04-publisher-uncertain-confirm-replay','Replayed original outbox event after successful broker delivery; no new compute attempt or business plan.')

    running=v.submit(token,available_date());wait_state(running['requestId'],{'RUNNING'})
    canceled=v.data('/api/tasks/'+str(running['taskId'])+'/cancel',{},token)
    final=complete(token,running);assert final['status']=='CANCELED'
    assert v.sql(f"SELECT COUNT(*) FROM production_schedule_plan WHERE source_request_id='{running['requestId']}'")=='0'
    assert v.sql(f"SELECT COUNT(*) FROM compute_attempt a JOIN compute_job j ON a.job_id=j.job_id WHERE j.request_id='{running['requestId']}' AND a.state IN ('RUNNING','LEASE_EXPIRED')",'industrial_algorithm')=='0'
    v.passed('T14-running-cancel','Python exit confirmed; task canceled; no plan applied.')

    configure(delay='1',scale=2)
    # Hold an already received result until after Core restarts, then apply that same receipt.
    v.sql("CREATE TRIGGER verification_delay_apply BEFORE INSERT ON algorithm_result_receipt FOR EACH ROW SET NEW.next_apply=TIMESTAMPADD(DAY,1,UTC_TIMESTAMP(3))")
    try:
        receipt=v.submit(token,available_date())
        v.wait(lambda:v.sql(f"SELECT state FROM algorithm_result_receipt WHERE request_id='{receipt['requestId']}'")=='READY')
        v.command(['restart','core'])
        v.wait(lambda:v.http('/api/tasks/'+str(receipt['taskId']),token=token)[1].get('code')==200)
    finally:v.sql('DROP TRIGGER verification_delay_apply')
    v.sql(f"UPDATE algorithm_result_receipt SET next_apply=UTC_TIMESTAMP(3) WHERE request_id='{receipt['requestId']}'")
    complete(token,receipt);v.assert_unique(receipt['requestId'])
    assert v.sql(f"SELECT attempt FROM compute_job WHERE request_id='{receipt['requestId']}'",'industrial_algorithm')=='1'
    v.passed('T10-receipt-restart','Core restart recovered a READY receipt and applied once without recalculating.')

    for mode,code in [('nonzero','ALGORITHM_NONZERO_EXIT'),('missing','OUTPUT_MISSING'),('invalid','OUTPUT_SCHEMA_INVALID')]:
        configure(mode=mode,delay='1')
        invalid=v.submit(token,available_date());failure(token,invalid,code)
        v.passed('T09-'+mode,{'errorCode':code,'requestId':invalid['requestId']})
    configure(delay='6',timeout='2')
    slow=v.submit(token,available_date());failure(token,slow,'COMPUTE_TIMEOUT')
    v.passed('T09-timeout','Finite retry policy exhausted; stable COMPUTE_TIMEOUT; no business plan.')
    configure(delay='1')

    # Break a derived energy detail, then retry the durable derivation without losing the schedule.
    v.sql("CREATE TRIGGER verification_fail_energy BEFORE INSERT ON energy_plan_detail FOR EACH ROW SET NEW.plan_id=IF(HOUR(NEW.timestamp)=3,NULL,NEW.plan_id)")
    try:
        derived=v.submit(token,available_date());done=complete(token,derived);assert done['resultId']
        v.wait(lambda:v.task(token,derived['taskId']).get('derivationStatus')=='PARTIAL_FAILED')
        assert v.task(token,derived['taskId'])['status']=='SUCCESS'
    finally:v.sql('DROP TRIGGER verification_fail_energy')
    v.sql(f"UPDATE plan_derivation_job SET next_run=UTC_TIMESTAMP(3) WHERE request_id='{derived['requestId']}'")
    v.wait(lambda:v.task(token,derived['taskId']).get('derivationStatus')=='COMPLETE')
    assert v.sql(f"SELECT COUNT(*) FROM energy_plan WHERE source_schedule_id={done['resultId']}")=='1'
    v.passed('T16-durable-derivation','Schedule remained available during an energy import rollback; retry produced one energy plan and completed evaluation.')
    # Database and broker restarts retain both task and immutable Agent run records.
    v.command(['restart','mysql','rabbitmq'])
    v.wait(lambda:v.command(['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqladmin -h127.0.0.1 -uroot ping --silent'],check=False).returncode==0)
    v.wait(lambda:v.http('/api/tasks/'+str(derived['taskId']),token=token)[1].get('code')==200)
    assert v.task(token,derived['taskId'])['status']=='SUCCESS';v.assert_unique(derived['requestId'])
    v.passed('D02-infrastructure-restart','MySQL and RabbitMQ restart retained history and business result.')

if __name__=='__main__':
    start=time.monotonic();success=False
    try:main();success=True
    except Exception as exception:v.records.append({'case':'execution','status':'FAIL','error':str(exception)});print(str(exception),flush=True)
    finally:
        report={'executedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'success':success,'elapsedSeconds':round(time.monotonic()-start,2),'fixture':'explicit slow process wrapper','records':v.records}
        directory=v.ROOT/'docs/microservices/verification-runs';directory.mkdir(parents=True,exist_ok=True)
        output=directory/(dt.datetime.now().strftime('%Y%m%d-%H%M%S-%f')+'-durability.json');output.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print('Saved report: '+str(output),flush=True)
    raise SystemExit(0 if success else 1)
