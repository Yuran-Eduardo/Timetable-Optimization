# Otimização de horários com NSGA-II
## Problematização, modelo e avaliação da primeira versão

**UC:** Otimização e Decisão em Engenharia de Software

**Autores:** Yuran e Sara

**Instituição:** Iscte - Instituto Universitário de Lisboa

**Entrega:** versão 1, preparada em novembro de 2026

### Síntese

O projeto procura propor horários e alocar salas a ocorrências letivas a partir dos dados fornecidos pelo grupo. Esta versão usa Java 21 e jMetal 7.9, mantendo a infraestrutura nativa de experiências do exemplo académico e uma formulação com dois objetivos de minimização.

| Dimensão | Versão 1 |
|---|---:|
| Ocorrências preservadas | 26 019 |
| Espaços no cadastro | 131 |
| Variáveis inteiras | 78 057 |
| Objetivos / constraints do jMetal | 2 / 0 |

O resultado de referência tem cobertura de todas as ocorrências, mas ainda contém conflitos e pendências. A capacidade de executar o algoritmo e produzir um CSV não demonstra, por si, qualidade ou viabilidade operacional.

O relatório apresenta o problema, os critérios de qualidade, as regras experimentais, a representação, o funcionamento da pesquisa e os resultados medidos. Distingue decisões do grupo de exemplos do enunciado e separa melhorias implementadas de trabalho futuro.

### Contributos desta versão

Leitura e validação dos dados completos; preservação de registos incompletos e duplicados; correspondências de salas rastreáveis; representação inteira; NSGA-II com operadores nativos; experiências e indicadores nativos; exportação de candidatos com diagnóstico; gravação de CSVs sem sobrescrita; testes e instruções de reprodução.

**Âmbito da avaliação:** salas e turmas identificáveis. Não são criadas colunas de docentes nem inferidas inscrições individuais.

<!-- PAGEBREAK -->

## 1. Problema e dados disponíveis

### Questão de investigação aplicada

Como atribuir dia, início e sala a cada ocorrência letiva, preservando a sua semana e duração, de modo a reduzir incompatibilidades de recursos e melhorar a organização do horário, usando apenas a informação disponível?

Os interessados são as turmas/estudantes, responsáveis pelos horários, cursos e gestão dos espaços. A qualidade tem várias dimensões: ausência de choques, respeito pelas salas pedidas, capacidade suficiente, organização temporal e aproveitamento dos espaços.

### Fontes de entrada

O horário de exemplo fornece curso, UC, turno, turma, inscritos, data, início, fim, pedido de característica e sala histórica. O cadastro fornece identificação das salas, capacidade normal e características. Os ficheiros estão em data/originais, no próprio repositório, e não exigem Excel ou base de dados para serem lidos.

A sala histórica é uma referência para análise; não fixa a sala que o algoritmo deve escolher. O identificador de sala é texto opaco. Não se deduz capacidade do nome e não se substitui a capacidade normal pelo valor histórico sem fonte e decisão explícitas.

### Unidade de decisão e cobertura

Cada registo é uma ocorrência. Uma aula partilhada por várias turmas continua a ser uma única ocorrência, ligada a todas essas turmas. As 26 019 ocorrências são preservadas, incluindo duplicados e informação em falta.

O cenário completo usa todas as datas conhecidas para delimitar o horizonte experimental entre 29/08/2022 e 11/06/2023. As aulas com data conhecida ficam na sua semana original. As 16 sem data permanecem sem atribuição temporal, identificadas e penalizadas; não recebem uma semana inventada.

Há 1 255 atividades que não necessitam de sala física. Continuam a ocupar as turmas quando a data é conhecida. As outras 24 764 ocorrências precisam de sala.

### Limites da informação

As turmas são tokens textuais, ainda sem revisão semântica integral. A contagem de inscritos não permite reconstruir horários de estudantes individuais. Não há identificação de docentes na fonte. Calendário oficial, encerramentos e perfis preferenciais não estão integralmente confirmados.

<!-- PAGEBREAK -->

## 2. Representação e regras de funcionamento

### Adaptação do molde NMMin

TimetableProblem estende diretamente AbstractIntegerProblem. O construtor usa os dados e a configuração do cenário como parâmetros estáticos, em vez de conservar os alvos artificiais n e m do problema de aproximação.

Cada ocorrência tem três genes: índice de dia elegível, índice de início e índice de sala elegível. Para N aulas, há 3N variáveis. No conjunto completo: 3 x 26 019 = 78 057 genes.

Os limites são calculados por gene. O total de aulas não é o limite do identificador de uma sala. Existem 131 salas no cadastro; cada domínio individual pode conter apenas uma parte delas.

### Condições impostas pelos domínios

| Condição | Tratamento |
|---|---|
| Semana e duração | Preservadas para datas conhecidas |
| Dias e funcionamento | Segunda a sábado, 08h-23h, por decisão experimental |
| Grelha | Inícios de 30 em 30 minutos |
| Característica | Correspondência explícita ou característica idêntica |
| Capacidade | Capacidade normal suficiente; sem relaxamento automático |
| Atividade sem sala | Não consome uma sala física |

A correspondência aprovada Sala/anfiteatro aulas significa Sala de Aulas normal OU Anfiteatro aulas. O texto original do pedido permanece separado da correspondência. Lab ISTA não recebe uma equivalência automática por ter usado certa sala no histórico.

### Condições avaliadas conjuntamente

Os choques de sala e turma dependem das escolhas das outras ocorrências e entram no objetivo 0. Os intervalos são considerados como início incluído e fim excluído: aulas contíguas não conflituam.

A regra experimental das três horas usa blocos da mesma UC/turma/data com pelo menos duas ocorrências. Pausa de 30 minutos interrompe o bloco; soma-se tempo letivo. Ocorrências individuais acima de três horas mantêm uma exceção provisória documentada. Esta interpretação é do grupo e não é apresentada como confirmação institucional.

Dia, hora, capacidade e requisitos não são alterados silenciosamente para tornar o problema mais fácil.

<!-- PAGEBREAK -->

## 3. Critérios de qualidade e funções objetivo

Existem exatamente dois objetivos de minimização e nenhum vetor de constraints com valores negativos. evaluate calcula as métricas e escreve diretamente solution.objectives()[0] e solution.objectives()[1].

### Objetivo 0: incompatibilidades e pendências

f0 = C_sala + C_turma + V_3h + D_lugares + U_sala + I_dados

C_sala e C_turma contam pares em sobreposição por recurso/data. Partilhar várias turmas não duplica o mesmo par dentro dessa família; o par pode contar em sala e turma. V_3h conta blocos acima do limite experimental.

D_lugares é o défice positivo de lugares, normalmente zero porque a capacidade suficiente é imposta no domínio. U_sala conta salas obrigatórias por atribuir. I_dados conta ocorrências com data, turma ou UC em falta.

Os estados desconhecidos são penalizações diagnósticas, não uma prova de que requisitos ainda desconhecidos sejam fisicamente impossíveis.

### Objetivo 1: qualidade flexível agregada

f1 = wp x P/N + wg x G/Bg + ws x S/Bs

P conta ocorrências fora da preferência de alguma turma; G soma janelas por turma/dia, sem bordas do dia ou desconto automático de almoço; S soma lugares excedentes positivos por ocorrência. Durante sobreposições, G usa a união dos intervalos ocupados.

Bg é o número de turmas distintas x dias civis do horizonte x minutos diários. Bs é a soma do maior excedente possível em cada domínio. São escalas fixadas para a instância, não recalculadas pela população.

No cenário completo, wp=0, wg=1 e ws=1. Os perfis preferenciais não foram fornecidos para todas as turmas: o critério fica não avaliado, não satisfeito.

### Consequência da dominância Pareto pura

O NSGA-II procura compromissos entre f0 e f1. Uma alternativa com mais conflitos pode continuar não dominada se melhorar f1. O nome rígido do primeiro objetivo não lhe atribui prioridade lexicográfica dentro da pesquisa.

Para apresentar um único CSV, escolhe-se depois o menor f0 e, em empate, o menor f1. Esta escolha posterior não altera a dominância usada pelo algoritmo.

<!-- PAGEBREAK -->

## 4. Algoritmo e infraestrutura experimental

NSGAIIStudy segue a organização do exemplo do professor: problemList, algorithmList, ExperimentBuilder e componentes nativos. São usados IntegerSBXCrossover e IntegerPolynomialMutation sobre IntegerSolution.

### Funcionamento de uma execução

O jMetal cria uma população inicial aleatória, avalia os dois objetivos, seleciona pais, aplica cruzamento e mutação e forma a população seguinte por ordenação não dominada e diversidade. Repete até ao orçamento de avaliações, controlado pelo próprio NSGA-II.

Não há motor de execução, contador manual de avaliações, temporizador ou reparação global próprios. Uma nova execução inicia outra pesquisa e não retoma o CSV anterior.

### Pipeline nativa

ExecuteAlgorithms; GenerateReferenceParetoFront; ComputeQualityIndicators; GenerateLatexTablesWithStatistics; GenerateWilcoxonTestTablesWithR; GenerateFriedmanTestTables; GenerateBoxplotsWithR.

A referência é empírica, obtida dos resultados das execuções; não é uma frente ótima conhecida. Os indicadores nativos usados são EP, GD, HV, IGD e IGD+. Spread e NHV foram excluídos pelos casos indefinidos em frentes discretas/degeneradas. R e LaTeX são opcionais para executar/compilar relatórios, não para gerar o horário.

### Configuração do teste completo V1

| Variante | População | Índice SBX | Índice de mutação |
|---|---:|---:|---:|
| NSGAIIa | 20 | 5 | 10 |
| NSGAIIb | 20 | 20 | 20 |
| NSGAIIc | 10 | 40 | 40 |
| NSGAIId | 20 | 80 | 80 |

Probabilidade de cruzamento 1,0; mutação 1/78 057 por gene. São 100 avaliações e duas execuções por variante, oito pesquisas independentes. Nas populações de 20, há apenas quatro gerações após a população inicial; na de 10, nove.

Este orçamento valida a execução com todos os dados. Não demonstra convergência. Como a semente não está fixada, repetir pode melhorar ou piorar o candidato. A versão 7.9 foi adotada para suportar Java 21 e corrigir casos de normalização encontrados nas versões anteriores, sem alterar a biblioteca.

<!-- PAGEBREAK -->

## 5. Dados incompletos e interpretação dos resultados

### Política explícita de diagnóstico

config/full.json permite executar com pendências reconhecidas, mantendo fullyEvaluable=false. O modo estrito config/real.json continua a bloquear essas pendências. Erros de leitura, durações inválidas e outros casos sem tratamento não são ignorados por essa opção.

Sem sala elegível, a atribuição é nula e penalizada. Não se usa uma sala fictícia. Sem data de origem, data e hora ficam em branco. Sem turma, o identificador permanece em branco. Os campos originais e o motivo são preservados no CSV.

### Cobertura de salas

| Situação | Ocorrências |
|---|---:|
| Domínio de sala não vazio | 24 092 |
| Não necessita de sala | 1 255 |
| Pedido não resolvido | 301 |
| Sem capacidade suficiente | 371 |
| Total | 26 019 |

Os pedidos não resolvidos incluem 285 Lab ISTA e 16 pedidos em branco. As 62 ocorrências estruturalmente incompletas são 16 sem data e 46 sem turma. Há sobreposição entre famílias de pendências; não se somam como se fossem aulas distintas.

### Limite inferior diagnóstico

Mesmo sem choques, U_sala=672 e I_dados=62 acrescentam 734 penalizações fixas. Assim, f0=0 não é alcançável na configuração atual apenas por aumentar gerações.

As 371 incompatibilidades de capacidade demonstram domínio individual vazio no cenário estrito. Os 301 pedidos desconhecidos não demonstram inviabilidade física: demonstram informação ainda não resolvida.

### O que o CSV certifica

O CSV certifica cobertura e rastreabilidade das ocorrências e apresenta um candidato com métricas. ASSIGNED_CHECK_GLOBAL_CONFLICTS indica atribuição local, não ausência de choques globais. As violações agregadas estão nas métricas e no resumo.

As datas sem origem não entram em conflitos temporais, pois não há semana conhecida; são penalizadas e continuam visíveis. Não se pode interpretar essa ausência de contagem como ausência real de conflito.

<!-- PAGEBREAK -->

## 6. Resultados medidos e qualidade da referência

A referência V1 corresponde à execução 20261101-231434-879. Foram verificadas as 26 019 ocorrências e recalculados independentemente os objetivos a partir do CSV.

| Componente de f0 | Valor |
|---|---:|
| Pares em conflito de sala | 9 515 |
| Pares em conflito de turma | 4 056 |
| Violações de sequências | 86 |
| Salas por atribuir | 672 |
| Ocorrências incompletas | 62 |
| Total | 14 391 |

São 15 654 ocorrências distintas envolvidas em pelo menos um choque de sala ou turma, cerca de 60,2%. Os pares não são aulas distintas: a mesma ocorrência pode participar em vários pares.

No dia 12/09/2022, no Auditório B1.04, Marketing Intelligence foi colocado às 09h30-11h00 e Marketing Operacional às 10h00-11h30: sobreposição de 60 minutos. Isto exemplifica a diferença entre uma sala individualmente adequada e uma atribuição conjunta viável.

### Objetivo flexível observado

G = 2 983 650 minutos; S = 809 182 lugares excedentes agregados por ocorrência. Bg = 136 640 700; Bs = 4 255 079.

f1 = 0,0218357342 + 0,1901685022 = 0,2120042363.

Não é uma percentagem de qualidade. Cerca de 89,7% da pontuação observada vem dos lugares, apesar dos pesos iguais. A normalização deve ser discutida.

### Comparação com a origem

O histórico tinha 931 pares de turma, contra 4 056 no gerado; as janelas agregadas eram 177 780 minutos, contra 2 983 650. Num subconjunto comparável de 23 115 ocorrências com sala nos dois horários, havia zero pares de sala no histórico e 8 853 no gerado. As violações experimentais de sequências desceram de 1 104 para 86.

O candidato ainda não demonstra melhoria global nem está pronto a utilizar. O histórico também não é certificado como plenamente viável: cadastro e semântica dos grupos têm limitações. A mesma identificação pode abranger alternativas curriculares; isso exige revisão, não remoção automática de conflitos.

<!-- PAGEBREAK -->

## 7. Melhorias, limitações e próximos passos

### Melhorias já implementadas

A primeira versão executa a pipeline nativa sobre todos os dados, respeita os domínios locais, conserva pendências e exporta métricas. Os resultados ficam em ficheiros novos, com configuração e objetivos associados, permitindo comparar pesquisas sem apagar as anteriores.

A entrega inclui 25 testes automatizados: fórmulas, regras temporais, compatibilidade, capacidade, cobertura integral, operadores inteiros, pipeline, exportação e preservação de CSVs consecutivos. Esses testes verificam o comportamento do programa, não a qualidade operacional do candidato.

### Limitações atuais

O orçamento é muito curto para 78 057 genes. A inicialização é aleatória e não usa o histórico como ponto de partida. O SBX inteiro opera sobre índices de sala, cuja proximidade numérica não significa semelhança física.

Faltam revisão semântica das turmas, perfis preferenciais e calendário institucional completo. Os dados não suportam docentes ou estudantes individuais. Há 635 ocorrências com zero inscritos e 180 individuais acima de três horas, mantidas com hipóteses provisórias.

Os resultados são estocásticos. Duas repetições não permitem declarar uma variante superior. Uma única instância e referências empíricas não suportam conclusões inferenciais fortes ou prova de ótimo. Os indicadores de estudos com referências diferentes não devem ser comparados diretamente sem controlo.

### Plano de melhoria

1. Usar o histórico como baseline e separar as 734 penalizações fixas da parcela reduzível.
2. Resolver/documentar pedidos, capacidade e campos ausentes, sem inventar dados nem alterar requisitos silenciosamente.
3. Definir os perfis experimentais e rever pesos/escalas do objetivo flexível.
4. Configurar sementes nativas para reprodução e recolher mais execuções independentes.
5. Aumentar o orçamento por etapas e avaliar custo e evolução dos objetivos antes de campanhas maiores.
6. Estudar decomposição semanal: há 33 semanas com datas conhecidas; a maior tem 2 296 ocorrências. Exige escalas globais e agregação definida, preservando o CSV integral e os casos sem data.

Estas medidas são propostas futuras. A versão V1 não afirma que já tenham sido implementadas ou que o horário gerado supere o histórico.

<!-- PAGEBREAK -->

## 8. Reprodução, entrega e referências

### Executar na máquina da Sara

Clonar/descarregar o repositório; abrir o pom.xml no IntelliJ; selecionar JDK 21 e recarregar Maven. Executar com.iscte.timetable.NSGAIIStudy sem argumentos. Alternativamente, usar EXECUTAR.cmd no Windows ou mvn compile exec:java no terminal.

A primeira importação precisa de internet para as dependências. Não exige base de dados, Excel, Python, R ou LaTeX para obter o CSV. O README contém instruções de diagnóstico e alternativas para Maven fora do PATH.

### Guardar e comparar resultados

Cada estudo cria em Resultados-Horarios-CSV um CSV com data, milissegundos e identificador, acompanhado de JSON com configuração, objetivos e origem. Não substitui CSVs anteriores. RESULTADOS.txt indica o mais recente. Resultados guarda os detalhes técnicos.

Horario-Referencia-V1.csv e o seu JSON são a referência versionada. Novos resultados continuam guardados localmente e são ignorados pelo Git, evitando crescimento automático do repositório. Para comparar, usar o mesmo âmbito e pesos e analisar pendências, conflitos e ambos os objetivos.

O repositório contém código, testes, configurações, dados necessários, documentação e referência. Caches, binários, logs, estado local do IDE e experiências antigas ficam fora do commit. FICHEIROS-PARA-COMMIT.md identifica a lista preparada.

### Decisões e fontes

O enunciado apresenta exemplos de requisitos e critérios; a formulação de dois objetivos, a interpretação de sequências, os perfis e os pesos são decisões de modelação do grupo. Não são apresentados como imposições textuais do professor.

Materiais consultados: Enunciado-Projectos.pdf, Projeto 1; FUC da UC; quatro conjuntos de slides fornecidos; exemplos NMMin.java e NSGAIIStudy.java. São referências de contexto, não dependências para executar esta entrega.

Referências técnicas primárias: documentação de experiências jMetal, https://jmetal.readthedocs.io/en/latest/experimentation.html; changelog e sementes, https://jmetal.readthedocs.io/en/stable/changelog.html.

A análise detalhada e o gráfico estão em docs/analise. As métricas da referência constam do JSON versionado. Esta documentação serve a problematização e a avaliação crítica da primeira versão, mantendo explícita a diferença entre cobertura dos dados, melhoria dos objetivos e viabilidade real.
