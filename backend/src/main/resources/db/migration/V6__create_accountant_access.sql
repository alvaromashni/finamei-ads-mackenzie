-- Vínculo entre um MEI e um contador (RN11). O MEI concede e pode revogar a
-- qualquer momento; revoked_at nulo significa acesso ativo. O histórico é
-- mantido: conceder de novo depois de revogar reativa a mesma linha.
CREATE TABLE accountant_access (
    id              UUID PRIMARY KEY,
    mei_id          UUID NOT NULL REFERENCES users (id),
    accountant_id   UUID NOT NULL REFERENCES users (id),
    granted_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at      TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_accountant_access_mei_accountant UNIQUE (mei_id, accountant_id),
    CONSTRAINT ck_accountant_access_distinct_users CHECK (mei_id <> accountant_id)
);

CREATE INDEX ix_accountant_access_mei_id ON accountant_access (mei_id);
CREATE INDEX ix_accountant_access_accountant_id ON accountant_access (accountant_id);
