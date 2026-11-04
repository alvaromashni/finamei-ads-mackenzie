package br.com.finamei.category;

import br.com.finamei.category.dto.CreateCategoryRequest;
import br.com.finamei.category.dto.RenameCategoryRequest;
import br.com.finamei.category.dto.UpdateCategoryStatusRequest;
import br.com.finamei.transaction.TransactionType;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> list(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) TransactionType type) {
        return ResponseEntity.ok(categoryService.list(userId, type));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.create(userId, request.name(), request.type()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> rename(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody RenameCategoryRequest request) {
        return ResponseEntity.ok(categoryService.rename(userId, id, request.name()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateStatus(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryStatusRequest request) {
        return ResponseEntity.ok(categoryService.setActive(userId, id, request.active()));
    }

    /** Exclui só categoria sem lançamentos; com lançamentos, responde 409 e o caminho é inativar (RN07). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        categoryService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
