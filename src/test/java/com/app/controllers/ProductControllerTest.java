package com.app.controllers;

import com.app.exceptions.ResourceNotFoundException;
import com.app.models.Category;
import com.app.models.CategoryRepository;
import com.app.models.Product;
import com.app.models.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductController productController;

    private Product testProduct;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
                .name("Test Product")
                .brand("Test Brand")
                .categoryId("cat-123")
                .build();
        testProduct.setId("prod-123");

        testCategory = Category.builder()
                .name("Fruits")
                .code("2877")
                .storeId("store-123")
                .build();
        testCategory.setId("cat-123");
    }

    @Test
    void searchProducts_WithQuery_ReturnsMatchingProducts() {
        Page<Product> page = new PageImpl<>(List.of(testProduct));
        when(productRepository.findByNameContainingIgnoreCaseOrBrandContainingIgnoreCase(
                eq("test"), eq("test"), any(PageRequest.class))).thenReturn(page);

        ResponseEntity<Page<Product>> response = productController.searchProducts(
                "test", null, 0, 20, "name", "asc");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotalElements());
    }

    @Test
    void searchProducts_WithoutQuery_ReturnsAllProducts() {
        Page<Product> page = new PageImpl<>(List.of(testProduct));
        when(productRepository.findAll(any(PageRequest.class))).thenReturn(page);

        ResponseEntity<Page<Product>> response = productController.searchProducts(
                null, null, 0, 20, "name", "asc");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotalElements());
        verify(productRepository).findAll(any(PageRequest.class));
    }

    @Test
    void searchProducts_BlankQuery_ReturnsAllProducts() {
        Page<Product> page = new PageImpl<>(List.of(testProduct));
        when(productRepository.findAll(any(PageRequest.class))).thenReturn(page);

        ResponseEntity<Page<Product>> response = productController.searchProducts(
                "   ", null, 0, 20, "name", "asc");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(productRepository).findAll(any(PageRequest.class));
    }

    @Test
    void searchProducts_InvalidSortField_ThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> productController.searchProducts(null, null, 0, 20, "invalidField", "asc"));
    }

    @Test
    void searchProducts_DescSortDirection_ReturnsSortedResults() {
        Page<Product> page = new PageImpl<>(List.of(testProduct));
        when(productRepository.findAll(any(PageRequest.class))).thenReturn(page);

        ResponseEntity<Page<Product>> response = productController.searchProducts(
                null, null, 0, 20, "name", "desc");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void searchProducts_WithPagination_ReturnsPagedResults() {
        Page<Product> page = new PageImpl<>(List.of(testProduct), PageRequest.of(1, 10), 20);
        when(productRepository.findAll(any(PageRequest.class))).thenReturn(page);

        ResponseEntity<Page<Product>> response = productController.searchProducts(
                null, null, 1, 10, "name", "asc");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(20, response.getBody().getTotalElements());
    }

    @Test
    void getProduct_WhenExists_ReturnsProduct() {
        when(productRepository.findById("prod-123")).thenReturn(Optional.of(testProduct));

        ResponseEntity<Product> response = productController.getProduct("prod-123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Test Product", response.getBody().getName());
    }

    @Test
    void getProduct_WhenNotFound_ThrowsResourceNotFoundException() {
        when(productRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> productController.getProduct("nonexistent"));
    }

    @Test
    void listCategories_ReturnsAllCategories() {
        when(categoryRepository.findAll()).thenReturn(List.of(testCategory));

        ResponseEntity<List<Category>> response = productController.listCategories();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("Fruits", response.getBody().get(0).getName());
    }

    @Test
    void getCategory_WhenExists_ReturnsCategory() {
        when(categoryRepository.findById("cat-123")).thenReturn(Optional.of(testCategory));

        ResponseEntity<Category> response = productController.getCategory("cat-123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Fruits", response.getBody().getName());
    }

    @Test
    void getCategory_WhenNotFound_ThrowsResourceNotFoundException() {
        when(categoryRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> productController.getCategory("nonexistent"));
    }

    @Test
    void getProductsByCategory_ReturnsProducts() {
        when(productRepository.findByCategoryId("cat-123")).thenReturn(List.of(testProduct));

        ResponseEntity<List<Product>> response = productController.getProductsByCategory("cat-123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }
}
