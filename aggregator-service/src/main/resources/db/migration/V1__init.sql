-- Расширение для генерации UUID
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ==============================
-- Таблица проектов
-- ==============================
CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(255),
    description TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_projects_name ON projects(name);

-- ==============================
-- Таблица репозиториев
-- ==============================
CREATE TABLE repositories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    active_branches INT DEFAULT 0,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    -- Составной уникальный ключ: имя репозитория + проект
    CONSTRAINT unique_repo_per_project UNIQUE (name, project_id)
);

CREATE INDEX idx_repositories_project ON repositories(project_id);
CREATE INDEX idx_repositories_name ON repositories(name);

-- ==============================
-- Таблица разработчиков
-- ==============================
CREATE TABLE developers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE       -- ← UNIQUE для ON CONFLICT (email)
);

CREATE INDEX idx_developers_name ON developers(name);
CREATE INDEX idx_developers_email ON developers(email);

-- ==============================
-- Таблица коммитов
-- ==============================
CREATE TABLE commits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    hash VARCHAR(40) NOT NULL UNIQUE,        -- хеш коммита глобально уникален
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    branch_name VARCHAR(255),
    lines_added INT DEFAULT 0,
    lines_deleted INT DEFAULT 0,

    developer_id UUID NOT NULL REFERENCES developers(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX idx_commits_developer ON commits(developer_id);
CREATE INDEX idx_commits_project ON commits(project_id);
CREATE INDEX idx_commits_created_at ON commits(created_at);
CREATE INDEX idx_commits_hash ON commits(hash);