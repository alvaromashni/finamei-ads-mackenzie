-- Registro de auditoria das escritas (ONF10). Tabela somente de INSERT: a aplicação
-- nunca atualiza nem apaga linhas (a entidade é @Immutable e o repositório não expõe
-- update/delete). Não guarda senha, hash nem token; "details" carrega só o mínimo para rastreio.
-- Sem FK para users de propósito: a trilha deve sobreviver a uma eventual remoção do usuário.
CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY,
    actor_user_id   UUID NOT NULL,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    action          VARCHAR(10) NOT NULL,
    resource_type   VARCHAR(30) NOT NULL,
    resource_id     UUID NOT NULL,
    details         VARCHAR(500),
    CONSTRAINT ck_audit_logs_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE'))
);

CREATE INDEX ix_audit_logs_actor_occurred_at ON audit_logs (actor_user_id, occurred_at);
CREATE INDEX ix_audit_logs_resource ON audit_logs (resource_type, resource_id);
