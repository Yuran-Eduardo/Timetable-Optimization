# Gestão de Horários do ISCTE: sugestão de salas com NSGA-II

Projeto de Sara Mateus e Yuran Eduardo para a UC de Otimização e Decisão em Engenharia de Software (ISCTE, 2026/2027).

O programa sugere uma sala para cada aula do horário do ISCTE de 2022/2023, mantendo o dia e a hora originais. Usa Java 21 e o algoritmo NSGA-II da biblioteca jMetal 7.9.

**Resultado dos ensaios anteriores no horário completo (26 019 aulas), com quatro objetivos:**
- 0 choques de sala e 0 aulas sem sala (regras hard);
- 713 aulas (3%) numa sala de tipo diferente do pedido;
- 9 lugares vazios por aula, em média;
- cerca de 3 minutos de execução por estudo com essa formulação.

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
No fim aparece um horário recomendado por problema, até seis destaques por frente e a localização da tabela com todas as alternativas. Não é necessário mudar de branch para comparar as duas formulações.

## 4. Onde ficam os resultados

- **`Resultados-Horarios-CSV/Timetable-<aulas>-2obj/` e `Timetable-<aulas>-4obj/`:** todos os horários com objetivos distintos da frente de cada problema, sem limite de seis. A opção 01 de cada pasta é a recomendada. Cada CSV tem um JSON com métricas, formulação, indicação de destaque e origem da solução. Abrir no Excel com UTF-8 e separador `;`.
- **`Resultados-Horarios-CSV/Comparacao-<data>-<id>.csv`:** tabela completa com `Problema`, `Numero_objetivos`, ficheiros das opções e métricas comuns: choques, aulas sem sala, tipo errado, lugares vazios e pontuação soft. Não mistura as frentes de Pareto.
- **`Resultados-Horarios-CSV/Destaques-<data>-<id>.csv`:** seleção de até seis opções de cada problema. As outras continuam na tabela completa e nos seus CSV. `RESULTADOS.txt` identifica os ficheiros e o recomendado de cada problema.
- **`Resultados/reais/<cenário>/<data>/`:** os detalhes técnicos do jMetal (FUN, VAR, indicadores e métricas de todos os horários alternativos).

Os novos resultados são guardados localmente e ignorados pelo Git. A pasta `Resultados-Horarios-CSV` ainda contém uma referência histórica V1 versionada, do modelo anterior; não deve ser confundida com o resultado da versão atual.

As alternativas pertencem à frente não dominada agregada das execuções de cada problema, respeitando a mesma prioridade hard do algoritmo. Não se comparam diretamente vetores de dimensões diferentes. Soluções com os mesmos objetivos contam como uma opção, mesmo que tenham atribuições diferentes. Todas essas opções são exportadas. O limite de seis aplica-se apenas aos destaques de cada frente: inclui o recomendado, os extremos e outros compromissos escolhidos pela distância de aglomeração do jMetal. Se a frente tiver menos de seis alternativas, todas ficam em destaque. Os FUN/VAR continuam disponíveis nos detalhes técnicos.

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

Na formulação de dois objetivos, quando todos os candidatos são viáveis, o objetivo hard fica em zero e a seleção favorece a menor penalização soft. A frente pode ter apenas um vetor distinto. Em quatro objetivos, tipo errado e lugares vazios podem originar vários compromissos. O programa apresenta o que foi encontrado, sem fabricar alternativas.

## 6. Organização do repositório

| Pasta ou ficheiro | Conteúdo |
|---|---|
| `src/main/java/com/iscte/timetable/NSGAIIStudy.java` | Ponto de entrada: configura e corre o NSGA-II (variante NSGAIIa) |
| `src/main/java/pt/iscte/timetable/` | Leitura e validação dos dados, problema (`TimetableProblem`), operadores com correção (`TimetableCrossover`, `TimetableMutation`) e exportação dos resultados |
| `config/full.json`, `config/teste.json` | Cenários do horário completo e da semana de teste |
| `config/compatibilidade-salas.json` | Correspondência entre o tipo de sala pedido e as características das salas |
| `config/real.json` | Cenário com os dados originais (usado também para localizar a raiz do projeto, não apagar) |
| `data/originais/` | Ficheiros recebidos, sem alterações |
| `data/tratados/` | Ficheiros corrigidos: `horario_completo.csv`, `horario_teste.csv`, `salas.csv` |
| `docs/` | Diagrama entidade-relação, diagrama de classes, fluxo de execução e scripts SQL da base de dados |
| `Resultados-Horarios-CSV/` | Horários publicados e metadados; a referência V1 é histórica |
| `docs/Guia-tecnico.md` | Descrição técnica da implementação atual e limitações |
| `EXECUTAR.cmd`, `Run-Study.ps1` | Lançadores para Windows |

## 7. Problemas frequentes

| Situação | Ação |
|---|---|
| Java incompatível | Confirmar o JDK 21 em Project Structure |
| Falha ao descarregar dependências | Verificar a internet e recarregar o projeto Maven |
| Acentos errados ou tudo numa coluna no Excel | Importar por Dados → De Texto/CSV, com UTF-8 e separador `;` |
