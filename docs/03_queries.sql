/* =====================================================================
   SCRIPT 03: queries de teste
   Selecionar UMA query (com o rato) e carregar em Execute (F5)
   para ver só o resultado dessa.
   ===================================================================== */
USE HorariosISCTE;
GO

/* ---------------------------------------------------------------
   Q1. Os dados entraram bem? Quantas linhas tem cada tabela
   --------------------------------------------------------------- */
SELECT 'EDIFICIO' AS tabela, COUNT(*) AS linhas FROM EDIFICIO
UNION ALL SELECT 'SALA', COUNT(*) FROM SALA
UNION ALL SELECT 'CARACTERISTICA', COUNT(*) FROM CARACTERISTICA
UNION ALL SELECT 'SALA_CARACTERISTICA', COUNT(*) FROM SALA_CARACTERISTICA
UNION ALL SELECT 'CURSO', COUNT(*) FROM CURSO
UNION ALL SELECT 'UC', COUNT(*) FROM UC
UNION ALL SELECT 'UC_CURSO', COUNT(*) FROM UC_CURSO
UNION ALL SELECT 'TURNO', COUNT(*) FROM TURNO
UNION ALL SELECT 'TURMA', COUNT(*) FROM TURMA
UNION ALL SELECT 'TURNO_TURMA', COUNT(*) FROM TURNO_TURMA
UNION ALL SELECT 'AULA', COUNT(*) FROM AULA;


/* ---------------------------------------------------------------
   Q2. Cada sala com o seu edifício e a lista de características
   (substitui as 30 colunas de "X" do Excel)
   --------------------------------------------------------------- */
SELECT  s.nome AS sala,
        e.nome AS edificio,
        s.cap_normal,
        STRING_AGG(c.codigo, ', ') AS caracteristicas
FROM SALA s
JOIN EDIFICIO e                  ON e.id_edificio = s.id_edificio
LEFT JOIN SALA_CARACTERISTICA sc ON sc.id_sala = s.id_sala
LEFT JOIN CARACTERISTICA c       ON c.id_caracteristica = sc.id_caracteristica
GROUP BY s.nome, e.nome, s.cap_normal
ORDER BY e.nome, s.nome;


/* ---------------------------------------------------------------
   Q3. O horário "legível": junta as tabelas e mostra cada aula
   como no Excel original (é a "junção" dos dois ficheiros)
   --------------------------------------------------------------- */
SELECT  a.id_aula, u.nome AS uc, t.codigo AS turno, t.inscritos,
        a.dia_semana, a.inicio, a.fim,
        cp.codigo AS caracteristica_pedida,
        s.nome AS sala, s.cap_normal
FROM AULA a
JOIN TURNO t                ON t.id_turno = a.id_turno
JOIN UC u                   ON u.id_uc = t.id_uc
LEFT JOIN CARACTERISTICA cp ON cp.id_caracteristica = a.id_caracteristica_pedida
LEFT JOIN SALA s            ON s.id_sala = a.id_sala
ORDER BY a.dia_semana, a.inicio;


/* ---------------------------------------------------------------
   Q4. Horário de uma turma (mudar o código à vontade)
   --------------------------------------------------------------- */
SELECT  tm.codigo AS turma, a.dia_semana, a.inicio, a.fim,
        u.nome AS uc, s.nome AS sala
FROM TURMA tm
JOIN TURNO_TURMA tt ON tt.id_turma = tm.id_turma
JOIN AULA a         ON a.id_turno = tt.id_turno
JOIN TURNO t        ON t.id_turno = a.id_turno
JOIN UC u           ON u.id_uc = t.id_uc
LEFT JOIN SALA s    ON s.id_sala = a.id_sala
WHERE tm.codigo = N'MEA1'
ORDER BY CHARINDEX(a.dia_semana, N'Seg Ter Qua Qui Sex Sáb'), a.inicio;


/* ===============================================================
   VERIFICAÇÃO DE REGRAS (ponto 2 do enunciado)
   =============================================================== */

/* ---------------------------------------------------------------
   Q5. REGRA CAPACIDADE: aulas com mais inscritos do que lugares
   --------------------------------------------------------------- */
SELECT  a.id_aula, u.nome AS uc, t.codigo AS turno,
        t.inscritos, s.nome AS sala, s.cap_normal,
        t.inscritos - s.cap_normal AS alunos_a_mais
FROM AULA a
JOIN TURNO t ON t.id_turno = a.id_turno
JOIN UC u    ON u.id_uc = t.id_uc
JOIN SALA s  ON s.id_sala = a.id_sala
WHERE t.inscritos > s.cap_normal
ORDER BY alunos_a_mais DESC;


/* ---------------------------------------------------------------
   Q6. REGRA TIPO DE SALA: a sala não tem a característica pedida
   --------------------------------------------------------------- */
SELECT  a.id_aula, u.nome AS uc, a.dia_semana, a.inicio,
        cp.codigo AS pedida, s.nome AS sala
FROM AULA a
JOIN TURNO t           ON t.id_turno = a.id_turno
JOIN UC u              ON u.id_uc = t.id_uc
JOIN SALA s            ON s.id_sala = a.id_sala
JOIN CARACTERISTICA cp ON cp.id_caracteristica = a.id_caracteristica_pedida
WHERE cp.codigo NOT IN (N'sem_sala', N'lab_ista')
  AND NOT EXISTS (SELECT 1 FROM SALA_CARACTERISTICA sc
                  WHERE sc.id_sala = a.id_sala
                    AND sc.id_caracteristica = a.id_caracteristica_pedida);


/* ---------------------------------------------------------------
   Q7. REGRA CONFLITO DE SALA: duas aulas na mesma sala, no mesmo
   dia, com horas sobrepostas (o esperado no horário real é 0)
   --------------------------------------------------------------- */
SELECT  s.nome AS sala, a1.data, a1.dia_semana,
        a1.id_aula AS aula_1, a1.inicio AS inicio_1, a1.fim AS fim_1,
        a2.id_aula AS aula_2, a2.inicio AS inicio_2, a2.fim AS fim_2
FROM AULA a1
JOIN AULA a2 ON a1.id_sala = a2.id_sala
            AND a1.data = a2.data
            AND a1.id_aula < a2.id_aula          -- cada par só uma vez
            AND a1.inicio < a2.fim
            AND a2.inicio < a1.fim               -- horas sobrepostas
JOIN SALA s ON s.id_sala = a1.id_sala;


/* ---------------------------------------------------------------
   Q8. REGRA CONFLITO DE TURMA: a mesma turma com duas aulas
   (de turnos diferentes) à mesma hora
   --------------------------------------------------------------- */
SELECT  tm.codigo AS turma, a1.data, a1.dia_semana,
        u1.nome AS uc_1, a1.inicio AS inicio_1, a1.fim AS fim_1,
        u2.nome AS uc_2, a2.inicio AS inicio_2, a2.fim AS fim_2
FROM TURMA tm
JOIN TURNO_TURMA t1 ON t1.id_turma = tm.id_turma
JOIN TURNO_TURMA t2 ON t2.id_turma = tm.id_turma AND t1.id_turno < t2.id_turno
JOIN AULA a1 ON a1.id_turno = t1.id_turno
JOIN AULA a2 ON a2.id_turno = t2.id_turno
            AND a1.data = a2.data
            AND a1.inicio < a2.fim
            AND a2.inicio < a1.fim
JOIN TURNO tu1 ON tu1.id_turno = t1.id_turno  JOIN UC u1 ON u1.id_uc = tu1.id_uc
JOIN TURNO tu2 ON tu2.id_turno = t2.id_turno  JOIN UC u2 ON u2.id_uc = tu2.id_uc
ORDER BY tm.codigo, a1.data, a1.inicio;


/* ---------------------------------------------------------------
   Q9. Aulas que precisam de sala mas não têm (ponto 4)
   --------------------------------------------------------------- */
SELECT  a.id_aula, u.nome AS uc, t.inscritos, a.dia_semana,
        a.inicio, a.fim, cp.codigo AS pedida
FROM AULA a
JOIN TURNO t                ON t.id_turno = a.id_turno
JOIN UC u                   ON u.id_uc = t.id_uc
LEFT JOIN CARACTERISTICA cp ON cp.id_caracteristica = a.id_caracteristica_pedida
WHERE a.id_sala IS NULL
  AND (cp.codigo IS NULL OR cp.codigo <> N'sem_sala');


/* ---------------------------------------------------------------
   Q10. Resumo para a apresentação: quantas violações de cada regra
   --------------------------------------------------------------- */
SELECT N'Inscritos > capacidade' AS regra, COUNT(*) AS violacoes
FROM AULA a JOIN TURNO t ON t.id_turno = a.id_turno
            JOIN SALA s  ON s.id_sala = a.id_sala
WHERE t.inscritos > s.cap_normal
UNION ALL
SELECT N'Sala sem a característica pedida', COUNT(*)
FROM AULA a JOIN CARACTERISTICA cp ON cp.id_caracteristica = a.id_caracteristica_pedida
WHERE a.id_sala IS NOT NULL
  AND cp.codigo NOT IN (N'sem_sala', N'lab_ista')
  AND NOT EXISTS (SELECT 1 FROM SALA_CARACTERISTICA sc
                  WHERE sc.id_sala = a.id_sala
                    AND sc.id_caracteristica = a.id_caracteristica_pedida)
UNION ALL
SELECT N'Conflito de sala (pares)', COUNT(*)
FROM AULA a1 JOIN AULA a2 ON a1.id_sala = a2.id_sala AND a1.data = a2.data
     AND a1.id_aula < a2.id_aula AND a1.inicio < a2.fim AND a2.inicio < a1.fim
UNION ALL
SELECT N'Aula sem sala (que precisa de sala)', COUNT(*)
FROM AULA a LEFT JOIN CARACTERISTICA cp ON cp.id_caracteristica = a.id_caracteristica_pedida
WHERE a.id_sala IS NULL AND (cp.codigo IS NULL OR cp.codigo <> N'sem_sala');
