# Horários gerados

Cada estudo concluído grava aqui um novo CSV, sem substituir os anteriores:

`Horario-AAAAMMDD-HHMMSS-milisegundos-identificador.csv`

Exemplo: `Horario-20261102-091500-123-a1b2c3d4.csv`.

O JSON com o mesmo nome guarda configuração, objetivos e origem da execução. A data e o identificador distinguem as execuções; não representam a qualidade do resultado.

`Horario-Referencia-V1.csv` e o respetivo JSON são uma referência histórica da primeira versão: 26 019 ocorrências, objetivo 0 de 14 391 e objetivo 1 de 0,2120042363. Esse modelo também alterava o dia e a hora e usava dois objetivos. Não é o resultado da implementação atual, nem um horário viável certificado. As métricas correspondem à execução 20261101-231434-879.

Os CSVs/JSONs de futuras execuções são ignorados pelo Git, mas permanecem no disco para comparação. Só a referência V1 e este README entram no commit. Para partilhar outro resultado, adicioná-lo explicitamente ao Git após revisão ou enviá-lo separadamente por decisão do grupo.

Os detalhes de cada estudo, incluindo vetores FUN/VAR, validação e estatísticas, ficam em Resultados/. RESULTADOS.txt na raiz indica o CSV mais recente.

Na versão atual, os quatro objetivos são choques de sala, aulas sem sala, tipo diferente do pedido e lugares vazios. O CSV é selecionado pelo menor total de violações hard e, em empate, pela menor pontuação soft ponderada. Estes valores não são diretamente comparáveis aos dois objetivos da referência histórica.

Para comparar candidatos atuais, verificar primeiro que os dados, as definições das métricas, os pesos e os parâmetros são comparáveis. No Excel, usar Estado, Choque_sala, Tipo_sala_certo e Lugares_vazios. As métricas globais hard_violations e soft_penalty repetem-se em todas as linhas; não são penalizações individuais.
