package com.app.controllers;

import com.app.models.Product;
import com.app.models.ProductRepository;
import com.app.models.Store;
import com.app.models.StoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductDetailViewController.class)
class ProductDetailViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private StoreRepository storeRepository;

    private Product createSampleProduct() {
        Product product = new Product();
        product.setId("prod-1");
        product.setName("Milk 2%");
        product.setBrand("Dairy Farm");
        product.setSize("4L");
        product.setUnit("L");
        product.setImageUrl("http://example.com/milk.jpg");
        return product;
    }

    private List<Store> createSampleStores() {
        Store store = new Store();
        store.setId("store-1");
        store.setName("Superstore");
        store.setCode("RCSS");
        store.setActive(true);
        return List.of(store);
    }

    @Test
    void productDetailPage_ValidProduct_ReturnsViewWithModel() throws Exception {
        Product product = createSampleProduct();
        List<Store> stores = createSampleStores();

        when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
        when(storeRepository.findByActiveTrue()).thenReturn(stores);

        mockMvc.perform(get("/products/prod-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("product-detail"))
                .andExpect(model().attributeExists("product"))
                .andExpect(model().attributeExists("stores"))
                .andExpect(model().attribute("product", product))
                .andExpect(model().attribute("stores", stores))
                .andExpect(model().attribute("selectedStoreId", (Object) null));
    }

    @Test
    void productDetailPage_WithStoreId_PassesStoreIdToModel() throws Exception {
        Product product = createSampleProduct();

        when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
        when(storeRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/products/prod-1").param("storeId", "store-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("product-detail"))
                .andExpect(model().attribute("selectedStoreId", "store-1"));
    }

    @Test
    void productDetailPage_WithoutStoreId_StoreIdIsNull() throws Exception {
        Product product = createSampleProduct();

        when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
        when(storeRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/products/prod-1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedStoreId", (Object) null));
    }

    @Test
    void productDetailPage_ProductNotFound_Returns404() throws Exception {
        when(productRepository.findById("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/products/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void productDetailPage_LoadsActiveStores() throws Exception {
        Product product = createSampleProduct();
        List<Store> stores = createSampleStores();

        when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
        when(storeRepository.findByActiveTrue()).thenReturn(stores);

        mockMvc.perform(get("/products/prod-1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("stores", stores));
    }

    @Test
    void productDetailPage_NoActiveStores_ReturnsEmptyStoreList() throws Exception {
        Product product = createSampleProduct();

        when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
        when(storeRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/products/prod-1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("stores", Collections.emptyList()));
    }
}
