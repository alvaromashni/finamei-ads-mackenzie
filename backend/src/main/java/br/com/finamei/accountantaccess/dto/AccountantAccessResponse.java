package br.com.finamei.accountantaccess.dto;

import br.com.finamei.accountantaccess.AccountantAccess;
import br.com.finamei.user.User;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountantAccessResponse(UUID id, AccountantSummary accountant, OffsetDateTime grantedAt) {

	public record AccountantSummary(String name, String email) {
	}

	public static AccountantAccessResponse from(AccountantAccess access, User accountant) {
		return new AccountantAccessResponse(
				access.getId(),
				new AccountantSummary(accountant.getName(), accountant.getEmail()),
				access.getGrantedAt());
	}
}
