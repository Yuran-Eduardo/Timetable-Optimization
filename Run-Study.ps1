# Localiza o Maven e executa o estudo ou os testes a partir da raiz do projeto.
param(
    [switch]$Demo,
    [switch]$RealExample,
    [switch]$ValidateOnly,
    [switch]$Test,
    [string]$Config,
    [string]$Output
)
$ErrorActionPreference = 'Stop'
$projectDirectory = $PSScriptRoot
$mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
$mavenExecutable = if ($mavenCommand) { $mavenCommand.Source } else { $null }
if (-not $mavenExecutable) {
    $jetbrainsDirectory = Join-Path $env:ProgramFiles 'JetBrains'
    if (Test-Path -LiteralPath $jetbrainsDirectory) {
        foreach ($installation in (Get-ChildItem -LiteralPath $jetbrainsDirectory -Directory | Sort-Object Name -Descending)) {
            # O nome da pasta do plugin varia entre versões do IntelliJ.
            foreach ($relativeMavenPath in @('plugins\maven-plugin\lib\maven3\bin\mvn.cmd', 'plugins\maven\lib\maven3\bin\mvn.cmd')) {
                $candidate = Join-Path $installation.FullName $relativeMavenPath
                if (Test-Path -LiteralPath $candidate) { $mavenExecutable = $candidate; break }
            }
            if ($mavenExecutable) { break }
        }
    }
}
if (-not $mavenExecutable) { throw 'Maven não encontrado. Usa o Maven do IntelliJ ou instala Maven no PATH.' }
Write-Host "Maven: $mavenExecutable"
$mavenArguments = @('-Dmaven.repo.local=./.m2-cache', '-U')
$argumentFile = $null
if ($Test) {
    $mavenArguments += 'test'
} else {
    $studyArguments = @()
    if ($Demo) { $studyArguments += '--demo' }
    if ($RealExample) { $studyArguments += '--real-example' }
    if ($ValidateOnly) { $studyArguments += '--validate-only' }
    if ($Config) { $studyArguments += "--config=$Config" }
    if ($Output) { $studyArguments += "--output=$Output" }
    $targetDirectory = Join-Path $projectDirectory 'target'
    New-Item -ItemType Directory -Path $targetDirectory -Force | Out-Null
    $relativeArgumentFile = 'target/launch-arguments-' + [guid]::NewGuid().ToString('N') + '.json'
    $argumentFile = Join-Path $projectDirectory $relativeArgumentFile
    $argumentJson = ConvertTo-Json -InputObject @($studyArguments) -Compress
    [System.IO.File]::WriteAllText($argumentFile, $argumentJson, [System.Text.UTF8Encoding]::new($false))
    $mavenArguments += @('compile', 'org.codehaus.mojo:exec-maven-plugin:3.5.0:java', "-Dexec.args=--arguments-file=$relativeArgumentFile")
}
Push-Location -LiteralPath $projectDirectory
try {
    & $mavenExecutable @mavenArguments
    if ($LASTEXITCODE -ne 0) { throw "Maven terminou com código $LASTEXITCODE" }
} finally {
    if ($argumentFile -and (Test-Path -LiteralPath $argumentFile)) { Remove-Item -LiteralPath $argumentFile }
    Pop-Location
}
