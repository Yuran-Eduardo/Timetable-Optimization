# Otimização de horários do ISCTE com NSGA-II

Primeira versão de Yuran e Sara para a UC de Otimização e Decisão em Engenharia de Software. Java 21 e jMetal 7.9, com a pipeline nativa de experiências do professor e dois objetivos de minimização.

**Esta versão processa todas as 26 019 ocorrências e 131 salas. O resultado ainda contém conflitos e pendências; não é um horário pronto a utilizar.**

## 1. O que a Sara precisa

- JDK 21 instalado e selecionado no projeto.
- IntelliJ IDEA com suporte Maven **ou** Maven disponível no terminal.
- Internet na primeira importação/compilação para descarregar as dependências do pom.xml.
- Os ficheiros desta pasta/repositório, incluindo config/ e data/. Não copiar apenas as classes Java.

Os testes desta entrega usaram Temurin 21.0.11 e Maven 3.9.11. Não é preciso instalar jMetal manualmente. Excel é opcional para consultar os CSVs; não é necessário para executar o algoritmo. R e LaTeX são opcionais e servem apenas para os relatórios estatísticos.

## 2. Obter e abrir o projeto

1. Clonar o repositório GitHub no IntelliJ (Get from Version Control), usando o URL que o grupo publicar, ou descarregar o ZIP e extraí-lo.
2. Abrir a pasta que contém este README e o pom.xml como projeto Maven.
3. Em File → Project Structure → Project SDK, selecionar JDK 21.
4. Recarregar o projeto no painel Maven e aguardar o fim do download das dependências.
5. Confirmar que existem data/originais/horario-com-salas.csv e data/originais/salas.csv.

O projeto não depende da pasta Desktop do Yuran nem de uma base de dados. Os caminhos dos dados são relativos aos JSONs em config/.

## 3. Executar no IntelliJ

1. Abrir src/main/java/com/iscte/timetable/NSGAIIStudy.java.
2. Carregar no botão Run junto do método main.
3. Não são necessários argumentos: o cenário padrão é config/full.json, com os dados completos.
4. Se for necessário ajustar a configuração, o diretório de trabalho deve ser a raiz do projeto, onde está pom.xml.

Também está incluída a configuração partilhada Executar horários em .run/. O IntelliJ pode mostrá-la na lista de configurações.

No início deve aparecer a instância Timetable-26019, com 26 019 aulas, 78 057 variáveis, dois objetivos e zero constraints. O teste padrão tem quatro variantes, duas execuções por variante e 100 avaliações por execução. População 20 nas variantes a/b/d e 10 na c. É um orçamento funcional inicial, não um estudo de convergência.

## 4. Executar no Windows sem configurar argumentos

Fazer duplo clique em EXECUTAR.cmd.

O lançador usa Maven do PATH ou procura o Maven instalado com IntelliJ em Program Files/JetBrains. Se o IntelliJ foi instalado por Toolbox noutro local e não houver Maven no PATH, usar o método do IntelliJ acima ou instalar/configurar Maven no PATH.

Também é possível abrir PowerShell na raiz do projeto:

```powershell
.\Run-Study.ps1
```

Se a política do PowerShell impedir abrir o .ps1 diretamente, usar EXECUTAR.cmd; o lançador aplica a configuração apenas ao processo que inicia.

## 5. Executar com Maven no terminal

Na raiz do projeto:

```text
java -version
mvn -version
mvn test
mvn compile exec:java
```

java e o Java mostrado por mvn -version devem ser da versão 21. Em Windows pode ser necessário usar mvn.cmd. Os scripts .cmd/.ps1 são para Windows; os comandos Maven também podem ser usados noutros sistemas com JDK 21 e Maven.

## 6. Encontrar o resultado e preservar o histórico

**Abrir a pasta Resultados-Horarios-CSV na raiz.** Cada estudo concluído cria um novo par:

```text
Resultados-Horarios-CSV/
  Horario-20261102-091500-123-a1b2c3d4.csv
  Horario-20261102-091500-123-a1b2c3d4.json
```

A data, os milissegundos e o identificador distinguem as execuções. O programa não substitui CSVs anteriores, mesmo que algum esteja aberto no Excel. O JSON com o mesmo nome guarda configuração, objetivos e ligação ao estudo técnico. RESULTADOS.txt na raiz indica o ficheiro mais recente.

No cenário completo, o CSV tem 26 019 registos de aulas mais o cabeçalho. Uma linha técnica sep=; eventualmente acrescentada pelo Excel não é uma aula. Se o Excel mostrar acentos incorretos ou tudo numa coluna, importar por Dados → De Texto/CSV, com UTF-8 e separador ponto e vírgula.

Resultados/ contém os detalhes de cada estudo: configuração, validação, FUN/VAR, indicadores e métricas das alternativas. No cenário completo, um CSV representativo é escolhido pelo menor objetivo 0 e, em empate, pelo menor objetivo 1. Os restantes vetores finais continuam nos ficheiros nativos.

## 7. Referência V1 e comparação entre execuções

O repositório inclui Resultados-Horarios-CSV/Horario-Referencia-V1.csv e o respetivo JSON. É a referência da execução 20261101-231434-879:

| Indicador | Valor |
|---|---:|
| Ocorrências | 26 019 |
| Objetivo 0 | 14 391 |
| Objetivo 1 | 0,2120042363 |
| Salas por atribuir | 672 |
| Ocorrências estruturalmente incompletas | 62 |

Este CSV serve para inspeção e comparação; não significa que a execução da Sara produza exatamente o mesmo horário. A semente ainda não está fixada, as pesquisas são estocásticas e uma nova execução pode melhorar ou piorar o candidato anterior. O algoritmo não retoma o CSV anterior.

Os novos CSVs/JSONs permanecem no disco, mas são ignorados pelo Git para não aumentar o repositório a cada execução. Só a referência V1 é incluída nesta entrega. Para comparar resultados, verificar dados, pesos, orçamento e as métricas; não usar apenas a data do nome ou o menor objetivo flexível.

## 8. Funções objetivo e pendências

- f0: conflitos de sala e turma, violações dos blocos da mesma UC/turma/dia acima de três horas, défices de capacidade, salas obrigatórias por atribuir e ocorrências com informação estrutural incompleta.
- f1: soma ponderada e normalizada de preferência horária, janelas por turma/dia e lugares excedentes. No cenário completo, a preferência tem peso zero e fica não avaliada; janelas e lugares têm peso 1.

Há exatamente dois objetivos e não se usa solution.constraints(). Capacidade suficiente, característica aprovada, duração, grelha e semana conhecida são respeitadas pelos domínios.

Não se fabricam dados: sem sala elegível, a atribuição fica em branco; sem data, data/hora ficam em branco; sem turma, o identificador permanece vazio. Estado assinala essas pendências. ASSIGNED_CHECK_GLOBAL_CONFLICTS não certifica ausência de choques globais.

As 672 salas por atribuir e as 62 ocorrências incompletas criam pelo menos 734 penalizações fixas na configuração atual. Mais avaliações não resolvem automaticamente esses dados. O relatório PDF explica as decisões e limitações.

## 9. Testar e escolher outro cenário

No PowerShell:

```powershell
.\Run-Study.ps1 -Test
.\Run-Study.ps1 -ValidateOnly
.\Run-Study.ps1 -Config config/real.json -ValidateOnly
.\Run-Study.ps1 -Demo
.\Run-Study.ps1 -RealExample
```

- Sem opções: dados completos, config/full.json.
- -ValidateOnly: diagnóstico do cenário padrão, sem otimização.
- config/real.json: modo estrito, com pendências que impedem executar.
- -Demo: dados sintéticos, apenas para demonstração/testes.
- -RealExample: recorte real de 98 aulas; não representa o horário completo.

O ficheiro config/full.json permite alterar população, avaliações, execuções, pesos e calendário. Fazer alterações explícitas e guardar a configuração junto dos resultados. A versão V1 foi verificada com 25 testes automatizados, incluindo cobertura de todas as ocorrências e gravação de dois CSVs consecutivos sem sobrescrita.

## 10. Documentação e organização

- docs/Relatorio-V1.pdf: problematização, modelo, qualidade, restrições, algoritmo, resultados e melhorias propostas.
- docs/Relatorio-V1.md: texto editável do relatório.
- docs/analise/: análise detalhada da referência e gráfico das alternativas.
- FICHEIROS-PARA-COMMIT.md: lista exata preparada para esta entrega.
- src/main/: implementação.
- src/test/: testes.
- config/: cenários e política de compatibilidade.
- data/: fontes, recorte rastreável e dados sintéticos.
- tools/prepare-real-example.py: ferramenta opcional para reproduzir o recorte; requer apenas Python 3, não é usada para executar o estudo completo.

A cache .m2-cache, target, metadados .idea, logs, arquivo e resultados experimentais locais não entram no commit. A pasta .run contém apenas uma configuração partilhada de execução.

Os componentes WithR geram scripts em Resultados/<execução>/NSGAIIStudy/R. R só é necessário para executar esses scripts; Render-Reports.ps1 aceita a pasta de execução com -Output. LaTeX só é necessário para compilar as tabelas .tex. Nada disso é obrigatório para obter o CSV.

## 11. Problemas frequentes

| Situação | Ação |
|---|---|
| Maven não encontrado pelo lançador | Executar pelo IntelliJ ou configurar Maven no PATH |
| Java incompatível | Confirmar JDK 21 no projeto e em mvn -version |
| Falha ao descarregar dependências | Verificar internet/proxy e recarregar Maven |
| CSV com 98 aulas | Foi selecionado -RealExample; executar sem opções para todos os dados |
| Sala/data em branco | Consultar Estado, validação e resumo; não é uma aula eliminada |
| Resultado diferente da referência | Esperado com aleatoriedade sem semente fixa |
| Mais avaliações, mas f0 nunca chega a zero | Existem pendências fixas; ver relatório e campos unassignedRooms/incompleteOccurrences |

Para a primeira publicação no GitHub, rever FICHEIROS-PARA-COMMIT.md e o staging antes de criar o commit. A definição de destino GitHub e o envio ficam separados desta preparação.
