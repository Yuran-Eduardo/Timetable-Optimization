# Gestão de Horários do ISCTE: sugestão de salas com NSGA-II (versão com 2 objetivos)

Projeto de Sara Mateus e Yuran Eduardo para a UC de Otimização e Decisão em Engenharia de Software (ISCTE, 2026/2027).

Este branch (`two-objectives`) contém a versão do programa com **dois objetivos**, que corresponde às fases A, B e C descritas na secção 5.4 do relatório. A solução final, com **quatro objetivos** (um por regra), está no `main`.

O programa sugere uma sala para cada aula do horário do ISCTE de 2022/2023, mantendo o dia e a hora originais. Usa Java 21 e o algoritmo NSGA-II da biblioteca jMetal 7.9.

**Resultado no horário completo (26 019 aulas, fase C):**
- 0 choques de sala e 0 aulas sem sala (regras hard);
- 713 aulas (3%) numa sala de tipo diferente do pedido;
- 9 lugares vazios por aula, em média;
- cerca de 3,4 minutos de execução.

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
Timetable-26019: 26019 aulas, 26019 variáveis, 2 objetivos, 1 constraint.
```
No fim aparece o resumo do melhor horário (`RESUMO DO MELHOR HORÁRIO`), com as violações de cada regra e os valores dos dois objetivos.

## 4. Onde ficam os resultados

- **`Resultados-Horarios-CSV/Horario-<data>.csv`:** o horário final, para abrir no Excel (Dados → De Texto/CSV, UTF-8, separador `;`). Cada linha é uma aula, com a sala atribuída e as colunas `Estado`, `Tipo_sala_certo`, `Lugares_vazios` e `Choque_sala`, que mostram as violações dessa aula.
- **`Resultados/reais/<cenário>/<data>/`:** os detalhes técnicos do jMetal (FUN, VAR, indicadores e métricas dos horários finais).

Estas duas pastas são criadas pelo programa e não fazem parte do repositório.

## 5. Como funciona

- **Variáveis:** uma por aula. O valor é a posição da sala numa lista de salas possíveis, que só contém salas de aula com lugares suficientes, ordenadas com o tipo pedido primeiro e da mais pequena para a maior.
- **Objetivos (ambos a minimizar):**
    - 0, regras hard: choques de sala + aulas sem sala;
    - 1, regras soft: aulas em sala de tipo errado + lugares vazios, cada parcela normalizada e multiplicada por um peso (`softWeights` em `config/full.json`).
- **Restrição:** o valor das regras hard. Um horário com violações hard perde sempre para um horário sem violações.
- **Horário inicial guloso (fase B):** o primeiro horário de cada execução coloca cada aula na primeira sala livre da sua lista.
- **Correção de choques (repair):** cada horário novo (população inicial, cruzamento e mutação) é corrigido para não ter duas aulas na mesma sala à mesma hora.
- **Parâmetros (fase C, `config/full.json`):** população 100, 5 000 avaliações, 2 execuções em 2 núcleos.

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
| `EXECUTAR.cmd`, `Run-Study.ps1` | Lançadores para Windows |

A documentação (diagramas e scripts SQL) e os resultados finais estão no `main`.

## 7. Problemas frequentes

| Situação | Ação |
|---|---|
| Java incompatível | Confirmar o JDK 21 em Project Structure |
| Falha ao descarregar dependências | Verificar a internet e recarregar o projeto Maven |
| Acentos errados ou tudo numa coluna no Excel | Importar por Dados → De Texto/CSV, com UTF-8 e separador `;` |
