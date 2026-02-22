package com.app.controllers;

import com.app.exceptions.ResourceNotFoundException;
import com.app.models.Product;
import com.app.models.ProductRepository;
import com.app.models.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ProductDetailViewController {

    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;

    @GetMapping("/products/{productId}")
    public String productDetailPage(
            @PathVariable String productId,
            @RequestParam(required = false) String storeId,
            Model model) {

        log.debug("Loading product detail page for product: {}, storeId: {}", productId, storeId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        model.addAttribute("product", product);
        model.addAttribute("stores", storeRepository.findByActiveTrue());
        model.addAttribute("selectedStoreId", storeId);

        return "product-detail";
    }
}
