# Horários gerados

Cada estudo concluído grava aqui um novo CSV, sem substituir os anteriores:

`Horario-AAAAMMDD-HHMMSS-milisegundos-identificador.csv`

Exemplo: `Horario-20261102-091500-123-a1b2c3d4.csv`.

O JSON com o mesmo nome guarda configuração, objetivos e origem da execução. A data e o identificador distinguem as execuções; não representam a qualidade do resultado.

`Horario-Referencia-V1.csv` e o respetivo JSON são a referência versionada desta entrega: 26 019 ocorrências, objetivo 0 de 14 391 e objetivo 1 de 0,2120042363. É um candidato com violações, não um horário viável certificado. As métricas correspondem à execução 20261101-231434-879.

Os CSVs/JSONs de futuras execuções são ignorados pelo Git, mas permanecem no disco para comparação. Só a referência V1 e este README entram no commit. Para partilhar outro resultado, adicioná-lo explicitamente ao Git após revisão ou enviá-lo separadamente por decisão do grupo.

Os detalhes de cada estudo, incluindo vetores FUN/VAR, validação e estatísticas, ficam em Resultados/. RESULTADOS.txt na raiz indica o CSV mais recente.

Para comparar candidatos, começar por ocorrências preservadas, conflitos, pendências e os dois objetivos, verificando que dados, pesos e parâmetros são comparáveis. A coluna Estado descreve pendências locais; ASSIGNED_CHECK_GLOBAL_CONFLICTS não garante ausência de choques.
