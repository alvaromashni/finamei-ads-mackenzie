-- Recuperação de senha (RN10), último acesso e versão de sessão (invalida tokens antigos).
ALTER TABLE users ADD COLUMN last_access_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN session_version INTEGER NOT NULL DEFAULT 0;

CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id),
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at     TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_password_reset_tokens_user_id ON password_reset_tokens (user_id);

-- Tentativa de acesso a lançamento de outro usuário (ONF05) entra na auditoria.
ALTER TABLE audit_logs DROP CONSTRAINT ck_audit_logs_action;
ALTER TABLE audit_logs ADD CONSTRAINT ck_audit_logs_action
    CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'DENY'));
