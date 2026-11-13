# Verification-only process fixture. Never used by the production Compose file.
import json,os,sys,time
mode=os.environ.get('VERIFICATION_MODE','normal')
if mode=='nonzero': raise SystemExit(3)
if mode=='missing': raise SystemExit(0)
if mode=='invalid':
    with open(sys.argv[2],'w') as output: json.dump({'unexpected':True},output)
    raise SystemExit(0)
time.sleep(float(os.environ.get('VERIFICATION_DELAY_SECONDS','6')))
sys.path.insert(0,'/app/algorithm')
from generate_plan import main
raise SystemExit(main(sys.argv))
