package br.com.finamei.audit;

public enum AuditAction {
	CREATE,
	UPDATE,
	DELETE,
	/** Tentativa recusada de acessar recurso de outro usuário (ONF05). */
	DENY
}
