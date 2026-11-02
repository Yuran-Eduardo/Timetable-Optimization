# Ficheiros preparados para o primeiro commit

Esta lista corresponde à versão 1 do projeto. Inclui **52 ficheiros**: código, testes, configurações, dados necessários, documentação e um CSV de referência. O índice Git foi preparado para revisão; criar o commit e publicar no GitHub são passos posteriores.

## Lista exata

```text
.gitattributes
.gitignore
.run/Executar-Horarios.run.xml
EXECUTAR.cmd
FICHEIROS-PARA-COMMIT.md
README.md
Render-Reports.ps1
Resultados-Horarios-CSV/Horario-Referencia-V1.csv
Resultados-Horarios-CSV/Horario-Referencia-V1.json
Resultados-Horarios-CSV/README.md
Run-Study.ps1
config/compatibilidade-salas.json
config/demo.json
config/full.json
config/real-example.json
config/real.json
data/exemplo-real/README.md
data/exemplo-real/horario.csv
data/exemplo-real/horario.manifest.json
data/originais/horario-com-salas.csv
data/originais/salas.csv
data/sinteticos/horario.csv
data/sinteticos/salas.csv
docs/Relatorio-V1.md
docs/Relatorio-V1.pdf
docs/analise/Analise-horario-completo.md
docs/analise/resultado-20261101-231434.png
pom.xml
src/main/java/com/iscte/timetable/NSGAIIStudy.java
src/main/java/pt/iscte/timetable/Assignment.java
src/main/java/pt/iscte/timetable/CsvDataLoader.java
src/main/java/pt/iscte/timetable/DataSet.java
src/main/java/pt/iscte/timetable/JsonFiles.java
src/main/java/pt/iscte/timetable/Lesson.java
src/main/java/pt/iscte/timetable/LessonDomain.java
src/main/java/pt/iscte/timetable/Preflight.java
src/main/java/pt/iscte/timetable/ResultExporter.java
src/main/java/pt/iscte/timetable/Room.java
src/main/java/pt/iscte/timetable/RoomPolicy.java
src/main/java/pt/iscte/timetable/ScheduleMetrics.java
src/main/java/pt/iscte/timetable/StudyConfig.java
src/main/java/pt/iscte/timetable/StudyInput.java
src/main/java/pt/iscte/timetable/TimeWindow.java
src/main/java/pt/iscte/timetable/TimetableProblem.java
src/test/java/pt/iscte/timetable/CsvDataLoaderTest.java
src/test/java/pt/iscte/timetable/Fixtures.java
src/test/java/pt/iscte/timetable/FullDataTest.java
src/test/java/pt/iscte/timetable/NSGAIIStudyTest.java
src/test/java/pt/iscte/timetable/PreflightAndOperatorsTest.java
src/test/java/pt/iscte/timetable/ResultExporterTest.java
src/test/java/pt/iscte/timetable/TimetableProblemTest.java
tools/prepare-real-example.py
```

## O que a Sara recebe

O projeto Maven completo, com JDK 21 como requisito, todos os dados para executar o cenário integral, os scripts Windows, a configuração partilhada de execução, os testes, o relatório PDF e uma referência de resultado. Não há dependência da pasta pessoal do Yuran. As dependências Java são obtidas pelo Maven na primeira importação.

A referência Horario-Referencia-V1.csv tem 26 019 ocorrências e contém violações conhecidas. O seu JSON permite interpretar e comparar esse resultado. Novos CSVs são guardados sem sobrescrita e ignorados pelo Git; continuam disponíveis localmente.

## Fora do commit

| Ficheiro/pasta | Motivo |
|---|---|
| target/ | Binários, testes, logs e ficheiros temporários regeneráveis |
| .m2-cache/ | Dependências locais; Maven descarrega na outra máquina |
| .idea/ | Preferências e estado local do IntelliJ; mantidos no disco |
| Resultados/ | Estudos completos gerados localmente; podem ser volumosos |
| Resultados-Horarios-CSV/ exceto README e referência V1 | Histórico de novas execuções, preservado localmente |
| HORARIO*.csv e RESULTADOS.txt da raiz | Nomes antigos/cópias locais e apontador da última execução |
| arquivo/, outputs/ | Experiências e documentação antigas, preservadas |
| tmp/, output/, *.log, __pycache__/ | Renderizações, logs e temporários |

As classes antigas NMMin, OccurrenceCrossover, OccurrenceMutation, ScheduleEvaluator, TimetableRun e TimetableStudy não integram a aplicação nem o staging final. As referências originais do professor, fora deste repositório, não foram apagadas. Os testes atuais têm os nomes das classes atuais.

## Verificação antes de criar o commit

```text
git status --short
git diff --cached --stat
git diff --cached --name-only
```

A lista acima deve coincidir com git diff --cached --name-only. Para verificar o código: Run-Study.ps1 -Test ou mvn test. A entrega foi verificada com 25 testes, incluindo ausência de sobrescrita dos CSVs. Uma cópia apenas dos ficheiros do índice também passou os 25 testes, sem configurações locais do IDE ou cache dentro dessa cópia. A verificação reutilizou uma cache Maven externa para as dependências, sem a incluir na entrega. Os CSVs de entrada e referência foram conferidos por checksum.

## Mensagem sugerida

```text
feat: add first full-data timetable optimization study
```

Depois de rever o conteúdo preparado, o grupo pode usar git commit com essa mensagem e configurar/publicar o remoto GitHub. Não existe um URL remoto configurado nesta preparação; usar o repositório escolhido pelo grupo. Nenhuma credencial deve ser adicionada aos ficheiros.
