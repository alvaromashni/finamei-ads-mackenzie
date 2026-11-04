package br.com.finamei.shared.error;

/**
 * A category with linked transactions cannot be deleted, only deactivated (RN07).
 * Logically deleted transactions count too: they still reference the category.
 */
public class CategoryInUseException extends RuntimeException {

	public CategoryInUseException() {
		super("Esta categoria tem lançamentos vinculados e não pode ser excluída. Inative-a para que ela deixe de aparecer em novos lançamentos.");
	}
}
