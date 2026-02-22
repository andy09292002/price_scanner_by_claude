package com.app.controllers;

import com.app.models.CategoryRepository;
import com.app.models.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class PriceListingViewController {

    private final StoreRepository storeRepository;
    private final CategoryRepository categoryRepository;

    @GetMapping("/products/listing")
    public String listingPage(Model model) {
        model.addAttribute("stores", storeRepository.findByActiveTrue());
        model.addAttribute("categories", categoryRepository.findAll());
        return "price-listing";
    }
}
