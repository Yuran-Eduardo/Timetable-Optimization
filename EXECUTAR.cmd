@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Run-Study.ps1"
if errorlevel 1 (
  echo A execucao falhou. Consulta as mensagens acima.
  pause
  exit /b 1
)
echo.
echo Horarios guardados em Resultados-Horarios-CSV. Consulta RESULTADOS.txt para o mais recente.
pause
