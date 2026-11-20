/* =====================================================================
   PROJETO: Gestão de Horários do ISCTE — Otimização e Decisão em ES
   SCRIPT 01: cria a base de dados e as 11 tabelas do diagrama ER
   Como usar: abrir no SSMS e carregar em Execute (F5).
   ===================================================================== */

-- 1) Criar a base de dados (apaga a anterior, se existir)
USE master;
GO
IF DB_ID(N'HorariosISCTE') IS NOT NULL
BEGIN
    ALTER DATABASE HorariosISCTE SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE HorariosISCTE;
END
GO
CREATE DATABASE HorariosISCTE;
GO
USE HorariosISCTE;
GO

/* ---------------- LADO DAS SALAS ---------------- */

CREATE TABLE EDIFICIO (
    id_edificio   INT            NOT NULL PRIMARY KEY,
    nome          NVARCHAR(100)  NOT NULL UNIQUE
);

CREATE TABLE SALA (
    id_sala       INT            NOT NULL PRIMARY KEY,
    nome          NVARCHAR(100)  NOT NULL UNIQUE,
    id_edificio   INT            NOT NULL REFERENCES EDIFICIO(id_edificio),
    cap_normal    INT            NOT NULL CHECK (cap_normal >= 0),
    cap_exame     INT            NULL     CHECK (cap_exame  >= 0)
);

CREATE TABLE CARACTERISTICA (
    id_caracteristica INT            NOT NULL PRIMARY KEY,
    codigo            NVARCHAR(50)   NOT NULL UNIQUE,   -- ex.: lab_informatica
    nome_original     NVARCHAR(100)  NOT NULL           -- ex.: Laboratório de Informática
);

-- Tabela de ligação N:M (cada "X" do Excel = 1 linha aqui)
CREATE TABLE SALA_CARACTERISTICA (
    id_sala           INT NOT NULL REFERENCES SALA(id_sala),
    id_caracteristica INT NOT NULL REFERENCES CARACTERISTICA(id_caracteristica),
    PRIMARY KEY (id_sala, id_caracteristica)
);

/* ---------------- LADO ACADÉMICO ---------------- */

CREATE TABLE CURSO (
    id_curso   INT           NOT NULL PRIMARY KEY,
    codigo     NVARCHAR(30)  NOT NULL UNIQUE          -- ex.: LEI
);

CREATE TABLE UC (
    id_uc      INT            NOT NULL PRIMARY KEY,
    nome       NVARCHAR(200)  NOT NULL UNIQUE
);

-- Tabela de ligação N:M (uma UC pode ser de vários cursos)
CREATE TABLE UC_CURSO (
    id_uc      INT NOT NULL REFERENCES UC(id_uc),
    id_curso   INT NOT NULL REFERENCES CURSO(id_curso),
    PRIMARY KEY (id_uc, id_curso)
);

CREATE TABLE TURNO (
    id_turno   INT           NOT NULL PRIMARY KEY,
    codigo     NVARCHAR(50)  NOT NULL UNIQUE,         -- ex.: L2205PL03
    id_uc      INT           NOT NULL REFERENCES UC(id_uc),
    inscritos  INT           NOT NULL CHECK (inscritos >= 0)
);

CREATE TABLE TURMA (
    id_turma   INT           NOT NULL PRIMARY KEY,
    codigo     NVARCHAR(50)  NOT NULL UNIQUE          -- ex.: ET-A9
);

-- Tabela de ligação N:M (um turno pode juntar várias turmas)
CREATE TABLE TURNO_TURMA (
    id_turno   INT NOT NULL REFERENCES TURNO(id_turno),
    id_turma   INT NOT NULL REFERENCES TURMA(id_turma),
    PRIMARY KEY (id_turno, id_turma)
);

/* ---------------- TABELA CENTRAL ---------------- */

CREATE TABLE AULA (
    id_aula                  INT          NOT NULL PRIMARY KEY,
    id_turno                 INT          NOT NULL REFERENCES TURNO(id_turno),
    dia_semana               NVARCHAR(3)  NOT NULL
        CHECK (dia_semana IN (N'Seg', N'Ter', N'Qua', N'Qui', N'Sex', N'Sáb')),
    inicio                   TIME(0)      NOT NULL,
    fim                      TIME(0)      NOT NULL,
    data                     DATE         NULL,
    id_caracteristica_pedida INT          NULL REFERENCES CARACTERISTICA(id_caracteristica),
    id_sala                  INT          NULL REFERENCES SALA(id_sala),   -- NULL = aula sem sala
    CONSTRAINT CK_AULA_horas CHECK (fim > inicio)
);
GO

PRINT N'Base de dados HorariosISCTE e 11 tabelas criadas com sucesso.';
