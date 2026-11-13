# Snapshot reproducible test/build/runtime evidence; never reads deployment credentials.
import datetime as dt,hashlib,json,os,pathlib,platform,subprocess,sys,xml.etree.ElementTree as ET,zipfile
import verify as v
DOC=v.ROOT/'docs/microservices'
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    suites=[]
    for path in sorted(v.ROOT.glob('*/target/surefire-reports/TEST-*.xml')):
        tree=ET.parse(path).getroot()
        suites.append({'module':path.relative_to(v.ROOT).parts[0],'name':tree.attrib['name'],'tests':int(tree.attrib['tests']),'failures':int(tree.attrib['failures']),'errors':int(tree.attrib['errors']),'skipped':int(tree.attrib['skipped']),'cases':[{'name':case.attrib['name'],'seconds':float(case.attrib.get('time',0)),'status':'FAIL' if case.find('failure') is not None or case.find('error') is not None else 'SKIP' if case.find('skipped') is not None else 'PASS'} for case in tree.findall('testcase')]})
    tests={'recordedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'totals':{key:sum(s[key] for s in suites) for key in ['tests','failures','errors','skipped']},'realModelEvaluated':False,'suites':suites}
    (DOC/'test-results.json').write_text(json.dumps(tests,ensure_ascii=False,indent=2),encoding='utf-8')
    modules=['AI-web','AI-algorithm-service','AI-agent-service','AI-gateway']
    jars=[]
    for module in modules:
        path=v.ROOT/module/'target'/(module+'-1.0-SNAPSHOT.jar')
        with zipfile.ZipFile(path) as archive:libs=[x.removeprefix('BOOT-INF/lib/') for x in archive.namelist() if x.startswith('BOOT-INF/lib/') and x.endswith('.jar')]
        jars.append({'path':path.relative_to(v.ROOT).as_posix(),'sha256':digest(path),'bytes':path.stat().st_size,'libraries':libs})
    mirrors=[]
    for schema,module in [('core','AI-web'),('algorithm','AI-algorithm-service'),('agent','AI-agent-service')]:
        for path in sorted((v.ROOT/'database/migrations'/schema).glob('*.sql')):
            mirror=v.ROOT/module/'src/main/resources/db/migration'/path.name
            assert path.read_bytes()==mirror.read_bytes(),str(path)
            mirrors.append({'schema':schema,'migration':path.name,'sha256':digest(path)})
    def healthy_containers():
        rows=[json.loads(line) for line in v.command(['ps','--format','json']).stdout.splitlines()]
        return rows if len(rows)==6 and all(c['Health']=='healthy' for c in rows) else None
    containers=v.wait(healthy_containers,90)
    images=[]
    for c in containers:
        info=json.loads(subprocess.run(['docker','image','inspect',c['Image'],'--format','{{json .}}'],check=True,capture_output=True,text=True).stdout)
        images.append({'service':c['Service'],'image':c['Image'],'imageId':info['Id'],'repoDigests':info.get('RepoDigests',[]),'health':c['Health']})
    docker=json.loads(subprocess.run(['docker','info','--format','{"cpus":{{.NCPU}},"memoryBytes":{{.MemTotal}},"serverVersion":"{{.ServerVersion}}","os":"{{.OperatingSystem}}"}'],capture_output=True,text=True,check=True).stdout)
    compatibility=v.ROOT.parent/'.cache/industrial-core-agent-compat.jar'
    manifest={'recordedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'host':{'platform':platform.platform(),'python':platform.python_version(),'logicalCpus':os.cpu_count()},'docker':docker,'algorithm':{'path':'data/algorithm/generate_plan.py','sha256':digest(v.ROOT/'data/algorithm/generate_plan.py')},'jars':jars,'migrations':mirrors,'images':images,'retainedCompatibilityJar':{'path':str(compatibility),'sha256':digest(compatibility)} if compatibility.exists() else None}
    (DOC/'artifact-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    report=json.loads(sorted((DOC/'verification-runs').glob('*.json'))[-1].read_text(encoding='utf-8'))
    normal=[json.loads(p.read_text(encoding='utf-8')) for p in sorted((DOC/'verification-runs').glob('*.json')) if p.stem[-1].isdigit() and '-boundaries' not in p.name and '-durability' not in p.name]
    example=next(record['details']['requestId'] for record in normal[-1]['records'] if record['case']=='normal-remote-closed-loop')
    phase=v.sql(f"""SELECT JSON_OBJECT('requestId',t.request_id,'workerQueueMs',TIMESTAMPDIFF(MICROSECOND,j.created_at,a.started_at)/1000,'computeMs',TIMESTAMPDIFF(MICROSECOND,a.started_at,a.completed_at)/1000,'resultDeliveryMs',TIMESTAMPDIFF(MICROSECOND,a.completed_at,r.created_at)/1000,'applicationWaitAndExecutionMs',TIMESTAMPDIFF(MICROSECOND,r.created_at,r.applied_at)/1000,'derivationMs',TIMESTAMPDIFF(MICROSECOND,d.created_at,d.completed_at)/1000,'details',(SELECT COUNT(*) FROM production_schedule_detail x WHERE x.schedule_id=t.result_id)) FROM algorithm_task t JOIN algorithm_result_receipt r ON r.request_id=t.request_id JOIN industrial_algorithm.compute_job j ON j.request_id=t.request_id JOIN industrial_algorithm.compute_attempt a ON a.job_id=j.job_id AND a.attempt=j.attempt JOIN plan_derivation_job d ON d.request_id=t.request_id WHERE t.request_id='{example}'""")
    pending=v.sql("SELECT JSON_OBJECT('pendingEvents',COUNT(*),'oldestAgeSeconds',IFNULL(MAX(TIMESTAMPDIFF(SECOND,created_at,UTC_TIMESTAMP(3))),0)) FROM core_outbox WHERE state='PENDING'")
    metrics={'scope':'One actual production-script sample, no pre-change performance comparison or throughput claim','phaseSample':json.loads(phase),'coreOutbox':json.loads(pending)}
    (DOC/'runtime-measurement.json').write_text(json.dumps(metrics,ensure_ascii=False,indent=2),encoding='utf-8')
    # Copy exact build logs into deliverable evidence, including baseline and final result.
    captures={'baseline.log':'baseline-build.txt','delivery-regression.log':'regression-build.txt','gateway-dns-build.log':'gateway-build.txt','delivery-frontend-build.log':'frontend-build.txt','delivery-frontend-lint.log':'frontend-lint.txt','dependency-tree.log':'../dependency-tree.txt'}
    folder=DOC/'build-evidence';folder.mkdir(exist_ok=True)
    for source,target in captures.items():
        path=v.ROOT.parent/'.cache'/source
        if path.exists():(folder/target).write_bytes(path.read_bytes())
    print('Evidence saved: '+json.dumps(tests['totals'])+'; six healthy services; hashes and phase sample recorded.')
if __name__=='__main__':main()
