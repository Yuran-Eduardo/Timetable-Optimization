# Gestão de Horários do ISCTE: sugestão de salas com NSGA-II

Projeto de Sara Mateus e Yuran Eduardo para a UC de Otimização e Decisão em Engenharia de Software (ISCTE, 2026/2027).

O programa sugere uma sala para cada aula do horário do ISCTE de 2022/2023, mantendo o dia e a hora originais. Usa Java 21 e o algoritmo NSGA-II da biblioteca jMetal 7.9.

**Resultado da execução final no horário completo (26 019 aulas):**
- 0 choques de sala e 0 aulas sem sala (regras hard), em todas as alternativas;
- horário recomendado: 713 aulas (3%) numa sala de tipo diferente do pedido e 9 lugares vazios por aula, em média;
- 2 objetivos: sempre 1 alternativa; 4 objetivos: entre 1 e 3 alternativas nas execuções que fizemos, com menos lugares vazios em troca de mais aulas em sala de tipo errado (o número varia entre execuções, devido às escolhas aleatórias do algoritmo);
- cerca de 7 minutos para as duas versões (2 execuções de cada, em 2 núcleos).

A versão atual executa as duas formulações no mesmo estudo: dois e quatro objetivos. Mantém os resultados separados e exige mais trabalho total do que executar apenas uma delas.

O relatório final explica o percurso e as decisões. A [documentação técnica](docs/Guia-tecnico.md) descreve as classes, os objetivos, a correção de choques, a seleção do CSV e as limitações da versão atual.

## 1. Requisitos

- JDK 21.
- IntelliJ IDEA (com Maven incluído) ou Maven no terminal.
- Internet na primeira compilação, para descarregar as dependências do `pom.xml`. Não é preciso instalar o jMetal à parte.

## 2. Abrir o projeto

1. Abrir no IntelliJ a pasta que contém este README e o `pom.xml`, como projeto Maven.
2. Em **File → Project Structure → Project SDK**, selecionar o JDK 21.
3. Recarregar o projeto no painel Maven e esperar pelo download das dependências.

## 3. Executar

Abrir `src/main/java/com/iscte/timetable/NSGAIIStudy.java` e carregar em **Run**. O cenário escolhe-se em **Run → Edit Configurations → Program arguments**:

| Objetivo | Argumentos |
|---|---|
| Correr o horário completo | nenhum (usa `config/full.json`) |
| Correr a semana de teste (2 296 aulas) | `--config=config/teste.json` |
| Só validar os dados (alguns segundos) | `--validate-only` ou `--validate-only --config=config/teste.json` |

A pasta de trabalho (working directory) deve ser a raiz do projeto. Em Windows também se pode fazer duplo clique em `EXECUTAR.cmd`, que corre o horário completo.

No início da execução deve aparecer:
```
Timetable-26019-2obj: 26019 aulas, 26019 variáveis, 2 objetivos, 1 restrição.
Timetable-26019-4obj: 26019 aulas, 26019 variáveis, 4 objetivos, 1 restrição.
```

No fim da execução, o programa devolve três coisas. Não é necessário mudar de branch para comparar as duas formulações.

**Consola:** um bloco por versão com o horário recomendado e, no fim, a lista de destaques e o ficheiro de comparação. Por exemplo:
```
===== HORÁRIO RECOMENDADO: Timetable-26019-4obj =====
[HARD] Pares de aulas na mesma sala à mesma hora: 0
[HARD] Aulas que precisam de sala e ficaram sem sala: 0
[SOFT] Aulas em sala de tipo diferente do pedido: 713 (3%)
[SOFT] Lugares vazios (soma): 234235 | média por aula: 9
Objetivos: [0.0, 0.0, 713.0, 234235.0]
Alternativas distintas na frente agregada: 1 | CSV disponíveis: 1 | Destaques: 1.
DESTAQUE Timetable-26019-4obj — OPÇÃO 1 (recomendada): ...\Resultados-Horarios-CSV\Timetable-26019-4obj\Horario-<data>-<id>-opcao-01.csv
COMPARAR NO EXCEL: ...\Resultados-Horarios-CSV\Comparacao-<data>-<id>.csv
```

**Janelas:** abrem dois gráficos, um por versão ("Frente de Pareto - 2 objetivos" e "Frente de Pareto - 4 objetivos"). Cada ponto é um horário alternativo (vermelho para 2 objetivos, azul para 4); quando a frente tem uma só alternativa, o gráfico mostra um único ponto; o eixo X mostra as aulas em sala de tipo diferente do pedido e o eixo Y o total de lugares vazios. O programa só termina depois de fechar as janelas. Sem ambiente gráfico (por exemplo, nos testes), as janelas não abrem e a execução continua normalmente.

**Pastas:** os horários e as tabelas de comparação ficam em `Resultados-Horarios-CSV/` e os detalhes técnicos em `Resultados/reais/` (ver secção 4).

## 4. Onde ficam os resultados

- **`Resultados-Horarios-CSV/Timetable-<aulas>-2obj/` e `Timetable-<aulas>-4obj/`:** todos os horários com objetivos distintos da frente de cada problema, sem limite de seis. A opção 01 de cada pasta é a recomendada. Cada CSV tem um JSON com métricas, formulação, indicação de destaque e origem da solução. Abrir no Excel com UTF-8 e separador `;`.
- **`Resultados-Horarios-CSV/Comparacao-<data>-<id>.csv`:** tabela completa com `Problema`, `Numero_objetivos`, ficheiros das opções e métricas comuns: choques, aulas sem sala, tipo errado, lugares vazios e pontuação soft. Não mistura as frentes de Pareto.
- **`Resultados-Horarios-CSV/Destaques-<data>-<id>.csv`:** seleção de até seis opções de cada problema. As outras continuam na tabela completa e nos seus CSV. `RESULTADOS.txt` identifica os ficheiros e o recomendado de cada problema.
- **`Resultados/reais/<cenário>/<data>/`:** os detalhes técnicos do jMetal (FUN, VAR, indicadores e métricas de todos os horários alternativos).

Os novos resultados são guardados localmente e ignorados pelo Git.

As alternativas pertencem à frente não dominada agregada das execuções de cada problema, respeitando a mesma prioridade hard do algoritmo. Não se comparam diretamente vetores de dimensões diferentes. Soluções com os mesmos objetivos contam como uma opção, mesmo que tenham atribuições diferentes. Todas essas opções são exportadas. O limite de seis é um teto e não uma quota: aplica-se apenas aos destaques de cada frente e inclui o recomendado, os extremos e outros compromissos escolhidos pela distância de aglomeração do jMetal. Se a frente tiver menos de seis alternativas, todas ficam em destaque. Os FUN/VAR continuam disponíveis nos detalhes técnicos.

Em cada pasta técnica de execução, `problemas/Timetable-<aulas>-<2 ou 4>obj/` guarda o resumo, o CSV recomendado, as alternativas e as métricas desse problema. Na raiz, `alternatives-summary.csv` compara todas as opções e `solution-summary.json` contém um resumo por problema. Os indicadores jMetal usam referências separadas: não se devem comparar diretamente valores brutos de hipervolume ou distância entre formulações com dimensões e escalas diferentes.

## 5. Como funciona

- **Variáveis:** uma por aula. O valor é a posição da sala numa lista de salas possíveis, que só contém salas de aula com lugares suficientes, ordenadas com o tipo pedido primeiro e da mais pequena para a maior.
- **Formulação com dois objetivos:** 0 = total das violações hard; 1 = tipo errado e lugares vazios, normalizados e ponderados por `softWeights`.
- **Formulação com quatro objetivos (todos a minimizar):**
  - 0: choques de sala (hard);
  - 1: aulas sem sala (hard);
  - 2: aulas em sala de tipo errado (soft);
  - 3: lugares vazios (soft).
- **Restrição:** a soma das regras hard. Um horário com violações hard perde sempre para um horário sem violações.
- **Horário inicial guloso:** o primeiro horário de cada execução coloca cada aula na primeira sala livre da sua lista.
- **Correção de choques (repair):** cada horário novo (população inicial, cruzamento e mutação) passa por uma tentativa de correção. Se nenhuma candidata estiver livre, o choque permanece e é contabilizado; não há garantia geral de viabilidade.
- **Parâmetros (`config/full.json`):** população 100 e 5 000 avaliações por execução de cada problema, com 2 execuções por formulação em 2 núcleos. No cenário completo, são quatro execuções e um orçamento total de 20 000 avaliações. Os pesos `softWeights` definem o objetivo soft na formulação de dois objetivos e a recomendação de CSV nas duas formulações. Em quatro objetivos não ponderam a procura.

**Porque podem existir poucas alternativas.** No fim de cada execução, o jMetal guarda nos ficheiros FUN e VAR os horários não dominados da população final; muitos são cópias com os mesmos valores, que contam como uma só opção. Na formulação de dois objetivos, quando todos os candidatos cumprem as regras hard, o objetivo hard fica em zero e a comparação depende apenas da penalização soft: um único horário é melhor do que todos os outros e a frente tem um só ponto. Em quatro objetivos, tipo errado e lugares vazios puxam em sentidos opostos e podem originar vários compromissos, mas só quando o algoritmo os encontra: no horário completo há 26 019 variáveis e cada mutação altera, em média, uma sala, por isso com 5 000 avaliações algumas execuções terminam apenas com o horário inicial guloso e outras encontram uma a três alternativas. Na semana de teste (2 296 aulas), com o mesmo orçamento, encontram-se mais alternativas. O programa apresenta o que foi encontrado, sem fabricar alternativas.

## 6. Organização do repositório

| Pasta ou ficheiro | Conteúdo |
|---|---|
| `src/main/java/com/iscte/timetable/NSGAIIStudy.java` | Ponto de entrada: configura e corre o NSGA-II (variante NSGAIIa) e mostra os gráficos da frente de Pareto |
| `src/main/java/pt/iscte/timetable/` | Leitura e validação dos dados, problema (`TimetableProblem`), operadores com correção (`TimetableCrossover`, `TimetableMutation`) e exportação dos resultados |
| `src/test/java/pt/iscte/timetable/` | Testes automáticos |
| `config/full.json`, `config/teste.json` | Cenários do horário completo e da semana de teste |
| `config/compatibilidade-salas.json` | Correspondência entre o tipo de sala pedido e as características das salas |
| `config/real.json` | Cenário com os dados originais (usado também para localizar a raiz do projeto, não apagar) |
| `data/originais/` | Ficheiros recebidos, sem alterações |
| `data/tratados/` | Ficheiros corrigidos: `horario_completo.csv`, `horario_teste.csv`, `salas.csv` |
| `docs/` | Diagrama entidade-relação, diagrama de classes, fluxo de execução e scripts SQL da base de dados |
| `docs/Guia-tecnico.md` | Descrição técnica da implementação atual e limitações |
| `Resultados-Horarios-CSV/` | Horários publicados, ficheiros de comparação e metadados |
| `EXECUTAR.cmd`, `Run-Study.ps1` | Lançadores para Windows |

## 7. Problemas frequentes

| Situação | Ação |
|---|---|
| Java incompatível | Confirmar o JDK 21 em Project Structure |
| Falha ao descarregar dependências | Verificar a internet e recarregar o projeto Maven |
| Acentos errados ou tudo numa coluna no Excel | Importar por Dados → De Texto/CSV, com UTF-8 e separador `;` |
| As janelas dos gráficos não abrem | Confirmar que o programa corre num ambiente com ecrã; os resultados em ficheiro são gerados na mesma |
