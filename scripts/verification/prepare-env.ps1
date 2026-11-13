param([string]$OutputPath = (Join-Path $PSScriptRoot '..\..\deploy\.env'))
$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $OutputPath) { Write-Output 'Existing deployment configuration retained.'; exit 0 }
$names = @('MYSQL_ROOT_PASSWORD','CORE_DB_PASSWORD','ALGORITHM_DB_PASSWORD','AGENT_DB_PASSWORD','MQ_PASSWORD','JWT_SECRET','AGENT_SERVICE_TOKEN','ALGORITHM_SERVICE_TOKEN')
$lines = foreach ($name in $names) { $bytes = [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32); $name + '=' + [Convert]::ToHexString($bytes).ToLowerInvariant() }
$lines += @('AGENT_CHAT_PROVIDER=none','AGENT_LLM_ENABLED=false','SPRING_AI_OPENAI_API_KEY=')
[IO.File]::WriteAllLines([IO.Path]::GetFullPath($OutputPath),$lines,[Text.UTF8Encoding]::new($false))
Write-Output 'Generated local deployment credentials; values are not printed.'
