param([Parameter(Mandatory=$true)][string]$Output)
$ErrorActionPreference = 'Stop'
$rCommand = Get-Command Rscript.exe -ErrorAction SilentlyContinue
$rExecutable = if ($rCommand) { $rCommand.Source } else { $null }
if (-not $rExecutable) {
    $rRoot = Join-Path $env:ProgramFiles 'R'
    if (Test-Path -LiteralPath $rRoot) {
        foreach ($installation in (Get-ChildItem -LiteralPath $rRoot -Directory | Sort-Object Name -Descending)) {
            $candidate = Join-Path $installation.FullName 'bin\Rscript.exe'
            if (Test-Path -LiteralPath $candidate) { $rExecutable = $candidate; break }
        }
    }
}
if (-not $rExecutable) { throw 'Rscript não encontrado. Instala R ou adiciona Rscript ao PATH.' }
$rDirectory = Join-Path (Resolve-Path -LiteralPath $Output).Path 'NSGAIIStudy\R'
if (-not (Test-Path -LiteralPath $rDirectory)) { throw "Não existe a pasta de scripts nativos: $rDirectory" }
Push-Location -LiteralPath $rDirectory
try {
    foreach ($script in (Get-ChildItem -LiteralPath $rDirectory -Filter '*.R' | Sort-Object Name)) {
        Write-Host "R: $($script.Name)"
        & $rExecutable $script.Name *> ($script.BaseName + '.log')
        if ($LASTEXITCODE -ne 0) { throw "Falhou $($script.Name); consultar o respetivo .log em $rDirectory" }
    }
} finally { Pop-Location }
Write-Host "Boxplots e tabelas Wilcoxon: $rDirectory"
