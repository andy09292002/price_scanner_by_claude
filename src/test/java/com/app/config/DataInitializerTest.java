package com.app.config;

import com.app.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private DataInitializer dataInitializer;

    private Store tntStore;
    private Store superstoreStore;

    @BeforeEach
    void setUp() {
        tntStore = Store.builder()
                .name("T&T Supermarket")
                .code("TNT")
                .active(true)
                .build();
        tntStore.setId("store-tnt");

        superstoreStore = Store.builder()
                .name("Real Canadian Superstore")
                .code("RCSS")
                .active(true)
                .build();
        superstoreStore.setId("store-rcss");
    }

    @Test
    void run_EmptyStoreRepo_InitializesStores() throws Exception {
        when(storeRepository.count()).thenReturn(0L);
        when(storeRepository.saveAll(anyList())).thenReturn(List.of());
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of());

        dataInitializer.run();

        verify(storeRepository).saveAll(anyList());
    }

    @Test
    void run_StoresExist_SkipsInitialization() throws Exception {
        when(storeRepository.count()).thenReturn(4L);
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of());

        dataInitializer.run();

        verify(storeRepository, never()).saveAll(anyList());
    }

    @Test
    void fixNumericCategories_SetsTntStoreId() throws Exception {
        Category tntCategory = Category.builder()
                .name(null)
                .code("2877")
                .build();
        tntCategory.setId("cat-1");

        when(storeRepository.count()).thenReturn(4L);
        when(categoryRepository.findAll()).thenReturn(List.of(tntCategory));
        when(storeRepository.findByCode("TNT")).thenReturn(Optional.of(tntStore));
        when(storeRepository.findByCode("RCSS")).thenReturn(Optional.of(superstoreStore));
        when(storeRepository.findByCode("PRICESMART")).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of());

        dataInitializer.run();

        verify(categoryRepository).save(argThat(cat ->
                "store-tnt".equals(cat.getStoreId()) && "Fruits".equals(cat.getName())));
    }

    @Test
    void fixNumericCategories_SkipsAlreadySetCategories() throws Exception {
        Category existingCategory = Category.builder()
                .name("Fruits")
                .code("2877")
                .storeId("store-tnt")
                .build();
        existingCategory.setId("cat-1");

        when(storeRepository.count()).thenReturn(4L);
        when(categoryRepository.findAll()).thenReturn(List.of(existingCategory));
        when(productRepository.findAll()).thenReturn(List.of());

        dataInitializer.run();

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void fixProductSizeAndUnit_ExtractsSizeFromName() throws Exception {
        Product product = Product.builder()
                .name("Organic Milk 2L")
                .build();
        product.setId("prod-1");

        when(storeRepository.count()).thenReturn(4L);
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of(product));

        dataInitializer.run();

        verify(productRepository).save(argThat(p ->
                "2".equals(p.getSize()) && "l".equals(p.getUnit())));
    }

    @Test
    void fixProductSizeAndUnit_SkipsProductWithExistingSize() throws Exception {
        Product product = Product.builder()
                .name("Organic Milk 2L")
                .size("2")
                .unit("l")
                .build();
        product.setId("prod-1");

        when(storeRepository.count()).thenReturn(4L);
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of(product));

        dataInitializer.run();

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void fixProductSizeAndUnit_SkipsNullProductName() throws Exception {
        Product product = Product.builder()
                .name(null)
                .build();
        product.setId("prod-1");

        when(storeRepository.count()).thenReturn(4L);
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of(product));

        dataInitializer.run();

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void run_CallsAllThreeMethods() throws Exception {
        when(storeRepository.count()).thenReturn(0L);
        when(storeRepository.saveAll(anyList())).thenReturn(List.of());
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of());

        dataInitializer.run();

        // Verify all three methods are called
        verify(storeRepository).count();
        verify(categoryRepository).findAll();
        verify(productRepository).findAll();
    }

    @Test
    void fixNumericCategories_NameBasedCode_FixesToNumericId() throws Exception {
        Category namedCategory = Category.builder()
                .name(null)
                .code("seafood")
                .build();
        namedCategory.setId("cat-2");

        when(storeRepository.count()).thenReturn(4L);
        when(categoryRepository.findAll()).thenReturn(List.of(namedCategory));
        when(storeRepository.findByCode("TNT")).thenReturn(Optional.of(tntStore));
        when(storeRepository.findByCode("RCSS")).thenReturn(Optional.of(superstoreStore));
        when(storeRepository.findByCode("PRICESMART")).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of());

        dataInitializer.run();

        verify(categoryRepository).save(argThat(cat ->
                "2880".equals(cat.getCode()) && "store-tnt".equals(cat.getStoreId())));
    }
}
