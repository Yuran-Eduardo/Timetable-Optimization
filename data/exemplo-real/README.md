# Recorte real — estudo nativo

Todas as 98 ocorrências do CSV original que incluem o token MEA1, incluindo 19 aulas partilhadas com MEPB1. Os valores de todas as colunas são preservados. Não foram filtradas aulas por incompatibilidade.

horario.manifest.json contém hashes, pressupostos e ligação de cada linha ao CSV integral. O estudo em config/real-example.json usa os primeiros 20, 50 e 98 registos deste recorte; problem-instances.json regista os subproblemas efetivamente executados. As 19 aulas partilhadas são mantidas como ocorrências únicas com ambas as turmas. Não são inventadas duas aulas para atingir 100.

As duas turmas recebem perfil experimental Tarde (13h–18h). Mantêm-se semana original, segunda a sábado 08h–23h e grelha de 30 minutos; não se incluem encerramentos adicionais neste cenário. São hipóteses experimentais, não calendário/preferências institucionais confirmados.

Usa-se o cadastro original completo de 131 salas e capacidades normais. A ocupação por aulas exteriores ao recorte não é modelada. Usam-se exclusivamente as informações existentes, avaliando conflitos de salas e turmas. O resultado é um estudo de subproblemas com dados reais, não a resolução das 26 019 ocorrências.

Reproduzir: python tools/prepare-real-example.py.
Executar: Run-Study.ps1 -RealExample.
