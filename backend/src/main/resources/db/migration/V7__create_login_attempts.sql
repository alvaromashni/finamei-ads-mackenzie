-- Tentativas de login malsucedidas por e-mail (RN09). A chave é o e-mail
-- normalizado, e não o usuário, para que e-mails inexistentes tenham o mesmo
-- comportamento observável que os existentes (a API não revela quais existem).
-- A linha some quando o login dá certo; locked_until nulo = sem bloqueio.
CREATE TABLE login_attempts (
    id                UUID PRIMARY KEY,
    email             VARCHAR(180) NOT NULL,
    failed_attempts   INTEGER NOT NULL DEFAULT 0,
    locked_until      TIMESTAMP WITH TIME ZONE,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_login_attempts_email UNIQUE (email),
    CONSTRAINT ck_login_attempts_non_negative CHECK (failed_attempts >= 0)
);
