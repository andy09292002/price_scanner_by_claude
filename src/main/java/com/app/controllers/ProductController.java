package com.app.controllers;

import com.app.models.Category;
import com.app.models.CategoryRepository;
import com.app.models.Product;
import com.app.models.ProductRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Endpoints for searching and viewing products")
public class ProductController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @GetMapping
    @Operation(summary = "Search products",
               description = "Search for products by name or brand.")
    public ResponseEntity<Page<Product>> searchProducts(
            @Parameter(description = "Search query (searches name and brand)")
            @RequestParam(required = false) String q,
            @Parameter(description = "Category ID to filter by")
            @RequestParam(required = false) String categoryId,
            @Parameter(description = "Page number")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sort direction (asc/desc)")
            @RequestParam(defaultValue = "asc") String sortDir) {

        log.debug("Searching products: q={}, categoryId={}, page={}, size={}",
                q, categoryId, page, size);

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        PageRequest pageRequest = PageRequest.of(page, size, sort);

        Page<Product> products;
        if (q != null && !q.isBlank()) {
            products = productRepository.findByNameContainingIgnoreCaseOrBrandContainingIgnoreCase(
                    q, q, pageRequest);
        } else {
            products = productRepository.findAll(pageRequest);
        }

        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product details",
               description = "Returns detailed information about a specific product.")
    public ResponseEntity<Product> getProduct(
            @Parameter(description = "Product ID")
            @PathVariable String id) {

        log.debug("Getting product: {}", id);
        return productRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/categories")
    @Operation(summary = "List categories",
               description = "Returns all product categories.")
    public ResponseEntity<List<Category>> listCategories() {
        log.debug("Listing categories");
        List<Category> categories = categoryRepository.findAll();
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/categories/{categoryId}")
    @Operation(summary = "Get category details",
               description = "Returns details of a specific category.")
    public ResponseEntity<Category> getCategory(
            @Parameter(description = "Category ID")
            @PathVariable String categoryId) {

        log.debug("Getting category: {}", categoryId);
        return categoryRepository.findById(categoryId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/categories/{categoryId}/products")
    @Operation(summary = "List products in category",
               description = "Returns all products in a specific category.")
    public ResponseEntity<List<Product>> getProductsByCategory(
            @Parameter(description = "Category ID")
            @PathVariable String categoryId) {

        log.debug("Getting products for category: {}", categoryId);
        List<Product> products = productRepository.findByCategoryId(categoryId);
        return ResponseEntity.ok(products);
    }
}
