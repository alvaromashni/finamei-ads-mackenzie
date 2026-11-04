package br.com.finamei.accountant.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Cliente (MEI) que concedeu acesso de leitura ao contador. {@code id} é o id do MEI. */
public record LinkedClientResponse(UUID id, String name, String email, OffsetDateTime grantedAt) {
}
