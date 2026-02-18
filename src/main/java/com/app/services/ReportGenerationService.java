package com.app.services;

import com.app.models.Category;
import com.app.models.CategoryRepository;
import com.app.models.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationService {

    private final CategoryRepository categoryRepository;

    private static final float PAGE_WIDTH = PDRectangle.LETTER.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.LETTER.getHeight();
    private static final float MARGIN = 50;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    private static final float LINE_HEIGHT = 14;
    private static final float SECTION_GAP = 20;
    private static final float IMAGE_SIZE = 40;
    private static final float ROW_HEIGHT = 50;

    public byte[] generateDiscountReportPdf(
            Map<String, PriceAnalysisService.StoreDiscountGroup> discountData,
            String storeFilter, String categoryFilter,
            int minDiscountPercentage) throws IOException {

        // Apply category filter if provided
        Map<String, PriceAnalysisService.StoreDiscountGroup> filteredData = discountData;
        if (categoryFilter != null && !categoryFilter.isBlank()) {
            filteredData = applyCategoryFilter(discountData, categoryFilter);
        }

        try (PDDocument document = new PDDocument()) {
            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(document, page);

            float y = PAGE_HEIGHT - MARGIN;

            // --- Header ---
            y = drawHeader(cs, fontBold, fontRegular, y, storeFilter, categoryFilter,
                    minDiscountPercentage, filteredData);

            // --- Store Sections ---
            for (Map.Entry<String, PriceAnalysisService.StoreDiscountGroup> entry : filteredData.entrySet()) {
                PriceAnalysisService.StoreDiscountGroup group = entry.getValue();
                if (group.items().isEmpty()) continue;

                // Check if we need a new page for the store header
                if (y < MARGIN + ROW_HEIGHT + SECTION_GAP + LINE_HEIGHT * 2) {
                    cs.close();
                    page = new PDPage(PDRectangle.LETTER);
                    document.addPage(page);
                    cs = new PDPageContentStream(document, page);
                    y = PAGE_HEIGHT - MARGIN;
                }

                // Store header
                y -= SECTION_GAP;
                cs.setFont(fontBold, 13);
                cs.beginText();
                cs.newLineAtOffset(MARGIN, y);
                String storeHeader = group.store().getName() + " (" + group.store().getCode() + ") - "
                        + group.itemCount() + " items on sale";
                cs.showText(storeHeader);
                cs.endText();
                y -= 3;

                // Separator line
                cs.setLineWidth(0.5f);
                cs.moveTo(MARGIN, y);
                cs.lineTo(PAGE_WIDTH - MARGIN, y);
                cs.stroke();
                y -= LINE_HEIGHT;

                // Column headers
                cs.setFont(fontBold, 8);
                cs.beginText();
                cs.newLineAtOffset(MARGIN + IMAGE_SIZE + 5, y);
                cs.showText("Product");
                cs.endText();
                cs.beginText();
                cs.newLineAtOffset(MARGIN + 250, y);
                cs.showText("Regular");
                cs.endText();
                cs.beginText();
                cs.newLineAtOffset(MARGIN + 310, y);
                cs.showText("Sale");
                cs.endText();
                cs.beginText();
                cs.newLineAtOffset(MARGIN + 365, y);
                cs.showText("Discount");
                cs.endText();
                cs.beginText();
                cs.newLineAtOffset(MARGIN + 430, y);
                cs.showText("Savings");
                cs.endText();
                y -= LINE_HEIGHT;

                // Product rows
                for (PriceAnalysisService.DiscountedItemDetail item : group.items()) {
                    if (y < MARGIN + ROW_HEIGHT) {
                        cs.close();
                        page = new PDPage(PDRectangle.LETTER);
                        document.addPage(page);
                        cs = new PDPageContentStream(document, page);
                        y = PAGE_HEIGHT - MARGIN;
                    }

                    y = drawProductRow(document, cs, fontRegular, fontBold, y, item);
                }
            }

            // Handle empty report
            if (filteredData.isEmpty() || filteredData.values().stream()
                    .allMatch(g -> g.items().isEmpty())) {
                y -= SECTION_GAP;
                cs.setFont(fontRegular, 12);
                cs.beginText();
                cs.newLineAtOffset(MARGIN, y);
                cs.showText("No discounted items found matching the specified criteria.");
                cs.endText();
            }

            cs.close();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    private float drawHeader(PDPageContentStream cs, PDType1Font fontBold, PDType1Font fontRegular,
                             float y, String storeFilter, String categoryFilter,
                             int minDiscountPercentage,
                             Map<String, PriceAnalysisService.StoreDiscountGroup> data) throws IOException {

        // Title
        cs.setFont(fontBold, 18);
        cs.beginText();
        cs.newLineAtOffset(MARGIN, y);
        cs.showText("Discount Report");
        cs.endText();
        y -= LINE_HEIGHT * 1.5f;

        // Generation date
        cs.setFont(fontRegular, 10);
        cs.beginText();
        cs.newLineAtOffset(MARGIN, y);
        cs.showText("Generated: " + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        cs.endText();
        y -= LINE_HEIGHT;

        // Filters applied
        StringBuilder filters = new StringBuilder("Filters: min " + minDiscountPercentage + "% off");
        if (storeFilter != null && !storeFilter.isBlank()) {
            filters.append(" | store: ").append(storeFilter);
        }
        if (categoryFilter != null && !categoryFilter.isBlank()) {
            filters.append(" | category: ").append(categoryFilter);
        }
        cs.beginText();
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(filters.toString());
        cs.endText();
        y -= LINE_HEIGHT;

        // Summary stats
        int totalItems = data.values().stream().mapToInt(PriceAnalysisService.StoreDiscountGroup::itemCount).sum();
        int storeCount = data.size();
        BigDecimal totalSavings = data.values().stream()
                .flatMap(g -> g.items().stream())
                .map(PriceAnalysisService.DiscountedItemDetail::discountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        cs.setFont(fontBold, 10);
        cs.beginText();
        cs.newLineAtOffset(MARGIN, y);
        cs.showText("Total: " + totalItems + " items across " + storeCount
                + " stores | Total potential savings: $" + totalSavings.setScale(2));
        cs.endText();
        y -= LINE_HEIGHT;

        // Header separator
        cs.setLineWidth(1f);
        cs.moveTo(MARGIN, y);
        cs.lineTo(PAGE_WIDTH - MARGIN, y);
        cs.stroke();
        y -= 5;

        return y;
    }

    private float drawProductRow(PDDocument document, PDPageContentStream cs,
                                 PDType1Font fontRegular, PDType1Font fontBold,
                                 float y, PriceAnalysisService.DiscountedItemDetail item) throws IOException {

        Product product = item.product();
        float rowStartY = y;

        // Try to draw product image
        float textX = MARGIN + IMAGE_SIZE + 5;
        if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
            try {
                byte[] imageBytes = fetchImage(product.getImageUrl());
                if (imageBytes != null) {
                    PDImageXObject image = PDImageXObject.createFromByteArray(document, imageBytes, "product");
                    cs.drawImage(image, MARGIN, y - IMAGE_SIZE + 10, IMAGE_SIZE, IMAGE_SIZE);
                }
            } catch (Exception e) {
                log.debug("Failed to load image for product {}: {}", product.getName(), e.getMessage());
                drawImagePlaceholder(cs, fontRegular, MARGIN, y - IMAGE_SIZE + 10);
            }
        } else {
            drawImagePlaceholder(cs, fontRegular, MARGIN, y - IMAGE_SIZE + 10);
        }

        // Product name (truncate if too long)
        cs.setFont(fontBold, 9);
        cs.beginText();
        cs.newLineAtOffset(textX, y);
        String name = truncateText(product.getName(), 35);
        cs.showText(name);
        cs.endText();
        y -= LINE_HEIGHT;

        // Brand and size
        cs.setFont(fontRegular, 8);
        cs.beginText();
        cs.newLineAtOffset(textX, y);
        String details = "";
        if (product.getBrand() != null && !product.getBrand().isBlank()) {
            details += product.getBrand();
        }
        if (product.getSize() != null && !product.getSize().isBlank()) {
            details += (details.isEmpty() ? "" : " | ") + product.getSize();
        }
        if (!details.isEmpty()) {
            cs.showText(truncateText(details, 40));
        }
        cs.endText();
        y -= LINE_HEIGHT;

        // Promo description
        if (item.promoDescription() != null && !item.promoDescription().isBlank()) {
            cs.setFont(fontRegular, 7);
            cs.beginText();
            cs.newLineAtOffset(textX, y);
            cs.showText(truncateText(item.promoDescription(), 45));
            cs.endText();
        }

        // Prices on the right side (aligned with row start)
        float priceY = rowStartY - 5;

        // Regular price
        cs.setFont(fontRegular, 9);
        cs.beginText();
        cs.newLineAtOffset(MARGIN + 250, priceY);
        cs.showText("$" + item.regularPrice().setScale(2));
        cs.endText();

        // Sale price
        cs.setFont(fontBold, 9);
        cs.beginText();
        cs.newLineAtOffset(MARGIN + 310, priceY);
        cs.showText("$" + item.salePrice().setScale(2));
        cs.endText();

        // Discount percentage
        cs.setFont(fontBold, 9);
        cs.beginText();
        cs.newLineAtOffset(MARGIN + 365, priceY);
        cs.showText(String.format("%.0f%% off", item.discountPercentage()));
        cs.endText();

        // Savings
        cs.setFont(fontRegular, 9);
        cs.beginText();
        cs.newLineAtOffset(MARGIN + 430, priceY);
        cs.showText("-$" + item.discountAmount().setScale(2));
        cs.endText();

        y = rowStartY - ROW_HEIGHT;

        // Light separator line
        cs.setLineWidth(0.25f);
        cs.moveTo(MARGIN, y + 5);
        cs.lineTo(PAGE_WIDTH - MARGIN, y + 5);
        cs.stroke();

        return y;
    }

    private void drawImagePlaceholder(PDPageContentStream cs, PDType1Font font,
                                      float x, float y) throws IOException {
        cs.setLineWidth(0.5f);
        cs.addRect(x, y, IMAGE_SIZE, IMAGE_SIZE);
        cs.stroke();
        cs.setFont(font, 6);
        cs.beginText();
        cs.newLineAtOffset(x + 3, y + 18);
        cs.showText("[No Image]");
        cs.endText();
    }

    byte[] fetchImage(String imageUrl) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(imageUrl).toURL().openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setInstanceFollowRedirects(true);

            if (conn.getResponseCode() != 200) {
                return null;
            }

            try (InputStream is = conn.getInputStream()) {
                byte[] rawBytes = is.readAllBytes();

                // Convert to PNG for PDFBox compatibility
                BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(rawBytes));
                if (bufferedImage == null) {
                    return null;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bufferedImage, "PNG", baos);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            log.debug("Failed to fetch image from {}: {}", imageUrl, e.getMessage());
            return null;
        }
    }

    private Map<String, PriceAnalysisService.StoreDiscountGroup> applyCategoryFilter(
            Map<String, PriceAnalysisService.StoreDiscountGroup> data, String categoryFilter) {

        List<Category> matchingCategories = categoryRepository.findByNameIgnoreCase(categoryFilter);
        if (matchingCategories.isEmpty()) {
            log.debug("No categories found matching filter: {}", categoryFilter);
            return Map.of();
        }

        Set<String> categoryIds = matchingCategories.stream()
                .map(Category::getId)
                .collect(Collectors.toSet());

        return data.entrySet().stream()
                .map(entry -> {
                    PriceAnalysisService.StoreDiscountGroup group = entry.getValue();
                    List<PriceAnalysisService.DiscountedItemDetail> filteredItems = group.items().stream()
                            .filter(item -> item.product().getCategoryId() != null
                                    && categoryIds.contains(item.product().getCategoryId()))
                            .toList();
                    return Map.entry(entry.getKey(),
                            new PriceAnalysisService.StoreDiscountGroup(
                                    group.store(), filteredItems.size(), filteredItems));
                })
                .filter(entry -> !entry.getValue().items().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        // Replace characters that PDFBox can't encode with standard fonts
        text = text.replaceAll("[^\\x20-\\x7E]", "");
        if (text.length() > maxLength) {
            return text.substring(0, maxLength - 3) + "...";
        }
        return text;
    }
}
