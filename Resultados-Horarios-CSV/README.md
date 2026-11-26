# Horários gerados

Cada estudo concluído grava aqui todos os CSV com objetivos distintos da frente de Pareto agregada, sem substituir os anteriores:

`Timetable-<aulas>-2obj/Horario-<data>-<id>-opcao-<numero>.csv`

`Timetable-<aulas>-4obj/Horario-<data>-<id>-opcao-<numero>.csv`

A opção 01 de cada problema é a recomendada. `Comparacao-<data>-<identificador>.csv` compara todas as opções com as colunas `Problema` e `Numero_objetivos` e os caminhos dos CSV. `Destaques-<data>-<identificador>.csv` mostra até seis opções por problema. A coluna `Destaque` permite identificar essas opções na tabela completa. O JSON de comparação guarda também a execução e a posição nativa de cada solução.

O JSON com o mesmo nome guarda configuração, objetivos e origem da execução. A data e o identificador distinguem as execuções; não representam a qualidade do resultado.

`Horario-Referencia-V1.csv` e o respetivo JSON são uma referência histórica da primeira versão: 26 019 ocorrências, objetivo 0 de 14 391 e objetivo 1 de 0,2120042363. Esse modelo também alterava o dia e a hora e usava dois objetivos. Não é o resultado da implementação atual, nem um horário viável certificado. As métricas correspondem à execução 20261101-231434-879.

Os CSVs/JSONs de futuras execuções são ignorados pelo Git, mas permanecem no disco para comparação. Só a referência V1 e este README entram no commit. Para partilhar outro resultado, adicioná-lo explicitamente ao Git após revisão ou enviá-lo separadamente por decisão do grupo.

Os detalhes de cada estudo, incluindo vetores FUN/VAR, validação e estatísticas, ficam em Resultados/. RESULTADOS.txt na raiz indica o CSV mais recente.

A versão atual executa dois problemas no mesmo estudo: `2obj` minimiza o total hard e a penalização soft ponderada; `4obj` minimiza separadamente choques de sala, aulas sem sala, tipo diferente e lugares vazios. Os domínios e a restrição hard são os mesmos. Cada problema tem uma frente agregada entre as suas execuções. As frentes de dimensões diferentes nunca são misturadas. A comparação comum mostra métricas de salas, não compara diretamente os indicadores geométricos entre dimensões diferentes.

Dentro de cada frente retiram-se soluções dominadas e objetivos repetidos, mas não se limita a quantidade de horários exportados. O limite de seis aplica-se só aos destaques de cada problema, preservando o recomendado e os extremos e usando a distância de aglomeração do jMetal. Com menos de seis, todas as opções ficam em destaque. Uma frente com um único vetor é apresentada como tal. A referência V1 usa dados e regras anteriores e não serve de comparação direta.

Para comparar candidatos atuais, verificar primeiro que os dados, as definições das métricas, os pesos e os parâmetros são comparáveis. No Excel, usar Estado, Choque_sala, Tipo_sala_certo e Lugares_vazios. As métricas globais hard_violations e soft_penalty repetem-se em todas as linhas; não são penalizações individuais.
