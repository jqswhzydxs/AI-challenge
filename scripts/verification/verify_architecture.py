from pathlib import Path
import json,xml.etree.ElementTree as ET,zipfile,hashlib
root=Path(__file__).resolve().parents[2]
forbidden={'AI-model','AI-mapper','AI-service','AI-common'}
ns={'m':'http://maven.apache.org/POM/4.0.0'}
for module in ['AI-agent-service','AI-algorithm-service','AI-gateway']:
    pom=ET.parse(root/module/'pom.xml')
    artifacts={entry.text for entry in pom.findall('./m:dependencies/m:dependency/m:artifactId',ns)}
    assert not artifacts.intersection(forbidden),(module,artifacts.intersection(forbidden))
    jar=root/module/'target'/f'{module}-1.0-SNAPSHOT.jar'
    with zipfile.ZipFile(jar) as archive:
        libs=[entry.split('/')[-1] for entry in archive.namelist() if entry.startswith('BOOT-INF/lib/')]
        assert not any(name.startswith(tuple(item+'-' for item in forbidden)) for name in libs),(module,'transitive Core dependency')
        if module=='AI-gateway':
            assert not any(name.startswith(('spring-webmvc-','spring-jdbc-','mysql-connector','spring-ai-')) for name in libs)
with zipfile.ZipFile(root/'AI-web/target/AI-web-1.0-SNAPSHOT.jar') as archive:
    assert not any('/spring-ai-' in name for name in archive.namelist()),'Core still loads Spring AI'
with zipfile.ZipFile(root/'AI-contracts/target/AI-contracts-1.0-SNAPSHOT.jar') as archive:
    assert not any('/entity/' in name or '/mapper/' in name for name in archive.namelist())
print('PASS: independent services have no Core entity/Mapper/service dependencies; Gateway is reactive; Core has no Spring AI.')
