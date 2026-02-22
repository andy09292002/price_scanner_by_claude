package com.app.controllers;

import com.app.models.*;
import com.app.services.PriceAnalysisService;
import com.app.services.PriceAnalysisService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({PriceListingController.class, PriceListingViewController.class})
class PriceListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PriceAnalysisService priceAnalysisService;

    @MockBean
    private StoreRepository storeRepository;

    @MockBean
    private CategoryRepository categoryRepository;

    private ProductListingResponse sampleStoreResponse;
    private CategoryListingResponse sampleCategoryResponse;

    @BeforeEach
    void setUp() {
        ProductPriceRow product1 = new ProductPriceRow(
                "prod-1", "Milk 2%", "Dairy Farm", "4L", "L",
                new BigDecimal("5.99"), new BigDecimal("3.99"), true, 33.4, null
        );
        ProductPriceRow product2 = new ProductPriceRow(
                "prod-2", "Bread", "Wonder", "675g", "g",
                new BigDecimal("3.49"), null, false, 0.0, null
        );

        CategoryGroup catGroup = new CategoryGroup("Dairy", "cat-1", List.of(product1));
        CategoryGroup catGroup2 = new CategoryGroup("Bakery", "cat-2", List.of(product2));

        StoreGroup storeGroup = new StoreGroup(
                "Superstore", "store-1", "RCSS", 2,
                List.of(catGroup, catGroup2)
        );

        sampleStoreResponse = new ProductListingResponse(
                List.of(storeGroup), 2, 1
        );

        CategoryStoreGroup catStoreGroup = new CategoryStoreGroup(
                "Superstore", "store-1", "RCSS", List.of(product1)
        );
        CategoryTopGroup catTopGroup = new CategoryTopGroup(
                "Dairy", "cat-1", 1, List.of(catStoreGroup)
        );

        sampleCategoryResponse = new CategoryListingResponse(
                List.of(catTopGroup), 1, 1
        );
    }

    @Test
    void getListing_ReturnsGroupedProducts() throws Exception {
        when(priceAnalysisService.getProductListingGroupedByStore(anyList(), anyList(), eq(false)))
                .thenReturn(sampleStoreResponse);

        mockMvc.perform(get("/api/products/listing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts").value(2))
                .andExpect(jsonPath("$.storeCount").value(1))
                .andExpect(jsonPath("$.groups[0].storeName").value("Superstore"))
                .andExpect(jsonPath("$.groups[0].productCount").value(2))
                .andExpect(jsonPath("$.groups[0].categories[0].categoryName").value("Dairy"))
                .andExpect(jsonPath("$.groups[0].categories[0].products[0].name").value("Milk 2%"));

        verify(priceAnalysisService).getProductListingGroupedByStore(anyList(), anyList(), eq(false));
    }

    @Test
    void getListing_WithStoreFilter_ReturnsFilteredResults() throws Exception {
        when(priceAnalysisService.getProductListingGroupedByStore(eq(List.of("store-1")), anyList(), eq(false)))
                .thenReturn(sampleStoreResponse);

        mockMvc.perform(get("/api/products/listing")
                        .param("storeIds", "store-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].storeId").value("store-1"));

        verify(priceAnalysisService).getProductListingGroupedByStore(eq(List.of("store-1")), anyList(), eq(false));
    }

    @Test
    void getListing_WithCategoryFilter_ReturnsFilteredResults() throws Exception {
        when(priceAnalysisService.getProductListingGroupedByStore(anyList(), eq(List.of("cat-1")), eq(false)))
                .thenReturn(sampleStoreResponse);

        mockMvc.perform(get("/api/products/listing")
                        .param("categoryIds", "cat-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts").value(2));

        verify(priceAnalysisService).getProductListingGroupedByStore(anyList(), eq(List.of("cat-1")), eq(false));
    }

    @Test
    void getListing_WithOnSaleOnly_ReturnsOnlySaleItems() throws Exception {
        ProductPriceRow saleProduct = new ProductPriceRow(
                "prod-1", "Milk 2%", "Dairy Farm", "4L", "L",
                new BigDecimal("5.99"), new BigDecimal("3.99"), true, 33.4, null
        );
        CategoryGroup catGroup = new CategoryGroup("Dairy", "cat-1", List.of(saleProduct));
        StoreGroup storeGroup = new StoreGroup("Superstore", "store-1", "RCSS", 1, List.of(catGroup));
        ProductListingResponse onSaleResponse = new ProductListingResponse(List.of(storeGroup), 1, 1);

        when(priceAnalysisService.getProductListingGroupedByStore(anyList(), anyList(), eq(true)))
                .thenReturn(onSaleResponse);

        mockMvc.perform(get("/api/products/listing")
                        .param("onSaleOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts").value(1))
                .andExpect(jsonPath("$.groups[0].categories[0].products[0].onSale").value(true));

        verify(priceAnalysisService).getProductListingGroupedByStore(anyList(), anyList(), eq(true));
    }

    @Test
    void getListing_WithNoProducts_ReturnsEmptyResponse() throws Exception {
        ProductListingResponse emptyResponse = new ProductListingResponse(Collections.emptyList(), 0, 0);

        when(priceAnalysisService.getProductListingGroupedByStore(anyList(), anyList(), eq(false)))
                .thenReturn(emptyResponse);

        mockMvc.perform(get("/api/products/listing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts").value(0))
                .andExpect(jsonPath("$.storeCount").value(0))
                .andExpect(jsonPath("$.groups").isEmpty());
    }

    @Test
    void getListing_GroupByCategory_ReturnsCorrectGrouping() throws Exception {
        when(priceAnalysisService.getProductListingGroupedByCategory(anyList(), anyList(), eq(false)))
                .thenReturn(sampleCategoryResponse);

        mockMvc.perform(get("/api/products/listing")
                        .param("groupBy", "category"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryCount").value(1))
                .andExpect(jsonPath("$.groups[0].categoryName").value("Dairy"))
                .andExpect(jsonPath("$.groups[0].stores[0].storeName").value("Superstore"));

        verify(priceAnalysisService).getProductListingGroupedByCategory(anyList(), anyList(), eq(false));
    }

    @Test
    void getListingPage_ReturnsViewName() throws Exception {
        when(storeRepository.findByActiveTrue()).thenReturn(Collections.emptyList());
        when(categoryRepository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/products/listing"))
                .andExpect(status().isOk())
                .andExpect(view().name("price-listing"))
                .andExpect(model().attributeExists("stores"))
                .andExpect(model().attributeExists("categories"));
    }
}
