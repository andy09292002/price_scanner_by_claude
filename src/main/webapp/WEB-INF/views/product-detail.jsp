<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${product.name} - Product Detail</title>
    <link rel="stylesheet" href="/css/theme.css">
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background: var(--bg-primary); color: var(--text-primary); }
        h1 { color: var(--text-primary); }

        .back-link { display: inline-block; margin-bottom: 16px; color: var(--accent); text-decoration: none; font-size: 14px; }
        .back-link:hover { text-decoration: underline; }

        .product-card {
            background: var(--bg-card); border-radius: 8px; padding: 24px; margin-bottom: 20px;
            box-shadow: var(--shadow); display: flex; gap: 24px; flex-wrap: wrap;
        }
        .product-image { width: 300px; height: 300px; object-fit: contain; border-radius: 8px; background: var(--bg-card-hover); }
        .no-image { width: 300px; height: 300px; background: var(--bg-no-image); border-radius: 8px; display: flex; align-items: center; justify-content: center; color: var(--text-muted); font-size: 14px; }
        .product-info { flex: 1; min-width: 250px; }
        .product-info h1 { margin-top: 0; font-size: 24px; }
        .product-info .detail { margin-bottom: 8px; font-size: 15px; color: var(--text-secondary); }
        .product-info .detail strong { color: var(--text-primary); }

        .section-title {
            font-size: 18px; font-weight: bold; color: var(--text-primary); margin-bottom: 12px; margin-top: 0;
            padding-bottom: 8px; border-bottom: 2px solid var(--accent);
        }

        .comparison-section {
            background: var(--bg-card); border-radius: 8px; padding: 24px; margin-bottom: 20px;
            box-shadow: var(--shadow);
        }
        .comparison-table { border-collapse: collapse; width: 100%; }
        .comparison-table th, .comparison-table td { padding: 10px 12px; text-align: left; border-bottom: 1px solid var(--border-color); font-size: 14px; }
        .comparison-table th { background: var(--bg-table-header); color: var(--text-secondary); font-weight: 600; }
        .comparison-table tr:hover { background: var(--bg-card-hover); }
        .price-sale { color: var(--danger); font-weight: bold; }
        .price-regular { color: var(--text-primary); }
        .price-original-struck { text-decoration: line-through; color: var(--text-muted); font-size: 12px; margin-right: 6px; }
        .discount-badge { background: var(--danger); color: #fff; padding: 2px 6px; border-radius: 10px; font-size: 11px; font-weight: bold; }
        .lowest-badge { background: var(--accent); color: #fff; padding: 2px 6px; border-radius: 10px; font-size: 11px; font-weight: bold; }

        .chart-section {
            background: var(--bg-card); border-radius: 8px; padding: 24px; margin-bottom: 20px;
            box-shadow: var(--shadow);
        }
        .zoom-buttons { display: flex; gap: 0; margin-bottom: 16px; }
        .zoom-buttons button {
            padding: 6px 16px; border: 1px solid var(--accent); background: var(--bg-card);
            color: var(--accent); cursor: pointer; font-size: 14px;
        }
        .zoom-buttons button:first-child { border-radius: 4px 0 0 4px; }
        .zoom-buttons button:last-child { border-radius: 0 4px 4px 0; }
        .zoom-buttons button.active { background: var(--accent); color: #fff; }
        .chart-container { position: relative; height: 400px; }

        .loading { text-align: center; padding: 20px; color: var(--text-muted); }
        .error-msg { text-align: center; padding: 20px; color: var(--danger); }
    </style>
</head>
<body>
    <a href="/products/listing" class="back-link">&larr; Back to Product Listing</a>

    <div class="product-card">
        <c:choose>
            <c:when test="${not empty product.imageUrl}">
                <img class="product-image" src="${product.imageUrl}" alt="${product.name}">
            </c:when>
            <c:otherwise>
                <div class="no-image">No Image Available</div>
            </c:otherwise>
        </c:choose>
        <div class="product-info">
            <h1>${product.name}</h1>
            <c:if test="${not empty product.brand}">
                <div class="detail"><strong>Brand:</strong> ${product.brand}</div>
            </c:if>
            <c:if test="${not empty product.size || not empty product.unit}">
                <div class="detail"><strong>Size:</strong> ${product.size} ${product.unit}</div>
            </c:if>
        </div>
    </div>

    <div class="comparison-section">
        <h2 class="section-title">Price Comparison Across Stores</h2>
        <div id="comparisonContent">
            <div class="loading">Loading price comparison...</div>
        </div>
    </div>

    <div class="chart-section">
        <h2 class="section-title">Price History</h2>
        <div class="zoom-buttons">
            <button data-days="7">7 Days</button>
            <button data-days="30" class="active">30 Days</button>
            <button data-days="365">365 Days</button>
        </div>
        <div class="chart-container">
            <canvas id="priceChart"></canvas>
        </div>
        <div id="chartLoading" class="loading" style="display:none;">Loading chart data...</div>
        <div id="chartError" class="error-msg" style="display:none;"></div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/chartjs-adapter-date-fns@3/dist/chartjs-adapter-date-fns.bundle.min.js"></script>
    <script>
        const productId = '${product.id}';
        const stores = [
            <c:forEach var="store" items="${stores}" varStatus="s">
                { id: '${store.id}', name: '${store.name}', code: '${store.code}' }<c:if test="${!s.last}">,</c:if>
            </c:forEach>
        ];
        const selectedStoreId = '${selectedStoreId}' || null;

        const CHART_COLORS = ['#4CAF50', '#2196F3', '#FF9800', '#9C27B0', '#F44336', '#00BCD4', '#795548', '#607D8B'];

        let priceChart = null;
        let currentDays = 30;

        function formatPrice(price) {
            if (price == null) return '-';
            return '$' + parseFloat(price).toFixed(2);
        }

        async function loadComparison() {
            const container = document.getElementById('comparisonContent');
            try {
                const resp = await fetch('/api/reports/compare/' + productId);
                if (!resp.ok) throw new Error('Failed to fetch comparison');
                const data = await resp.json();

                const entries = Object.entries(data.storePrices || {});
                if (entries.length === 0) {
                    container.innerHTML = '<div class="loading">No price data available for this product.</div>';
                    return;
                }

                const lowestPrice = data.lowestPrice != null ? parseFloat(data.lowestPrice) : null;

                let html = '<table class="comparison-table"><thead><tr>';
                html += '<th>Store</th><th>Price</th><th>On Sale</th><th>Promo</th><th></th><th></th>';
                html += '</tr></thead><tbody>';

                for (const [storeCode, sp] of entries) {
                    const price = parseFloat(sp.price);
                    const storeName = sp.store ? sp.store.name : storeCode;
                    const isLowest = lowestPrice != null && price === lowestPrice;

                    html += '<tr>';
                    html += '<td>' + storeName + '</td>';

                    if (sp.onSale) {
                        html += '<td><span class="price-sale">' + formatPrice(sp.price) + '</span></td>';
                    } else {
                        html += '<td><span class="price-regular">' + formatPrice(sp.price) + '</span></td>';
                    }

                    html += '<td>' + (sp.onSale ? '<span class="discount-badge">Sale</span>' : '-') + '</td>';
                    html += '<td>' + (sp.promoDescription || '-') + '</td>';
                    html += '<td>' + (isLowest ? '<span class="lowest-badge">Lowest</span>' : '') + '</td>';
                    html += '<td>' + (sp.sourceUrl ? '<a href="' + sp.sourceUrl + '" target="_blank" rel="noopener noreferrer" style="color: var(--accent); text-decoration: none;">View in Store &rarr;</a>' : '') + '</td>';
                    html += '</tr>';
                }

                html += '</tbody></table>';
                container.innerHTML = html;
            } catch (e) {
                container.innerHTML = '<div class="error-msg">Unable to load price comparison.</div>';
            }
        }

        async function loadChartData(days) {
            currentDays = days;
            const loading = document.getElementById('chartLoading');
            const errorEl = document.getElementById('chartError');
            loading.style.display = 'block';
            errorEl.style.display = 'none';

            if (priceChart) {
                priceChart.destroy();
                priceChart = null;
            }

            const datasets = [];
            let hasData = false;

            for (let i = 0; i < stores.length; i++) {
                const store = stores[i];
                try {
                    const resp = await fetch('/api/reports/history/' + productId + '?storeId=' + store.id + '&days=' + days);
                    if (!resp.ok) continue;
                    const data = await resp.json();

                    if (data.pricePoints && data.pricePoints.length > 0) {
                        hasData = true;
                        const points = data.pricePoints.map(p => ({
                            x: p.timestamp,
                            y: parseFloat(p.price)
                        }));

                        datasets.push({
                            label: store.name,
                            data: points,
                            borderColor: CHART_COLORS[i % CHART_COLORS.length],
                            backgroundColor: CHART_COLORS[i % CHART_COLORS.length] + '20',
                            borderWidth: 2,
                            pointRadius: days > 60 ? 1 : 3,
                            tension: 0.1,
                            fill: false
                        });
                    }
                } catch (e) {
                    // skip store on error
                }
            }

            loading.style.display = 'none';

            if (!hasData) {
                errorEl.textContent = 'No price history data available for the selected period.';
                errorEl.style.display = 'block';
                return;
            }

            const ctx = document.getElementById('priceChart').getContext('2d');
            priceChart = new Chart(ctx, {
                type: 'line',
                data: { datasets: datasets },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    interaction: { mode: 'index', intersect: false },
                    scales: {
                        x: {
                            type: 'time',
                            time: { unit: days <= 7 ? 'day' : days <= 30 ? 'day' : 'week' },
                            title: { display: true, text: 'Date' }
                        },
                        y: {
                            title: { display: true, text: 'Price ($)' },
                            ticks: { callback: function(value) { return '$' + value.toFixed(2); } }
                        }
                    },
                    plugins: {
                        tooltip: {
                            callbacks: {
                                label: function(context) {
                                    return context.dataset.label + ': $' + context.parsed.y.toFixed(2);
                                }
                            }
                        }
                    }
                }
            });
        }

        // Zoom button handlers
        document.querySelectorAll('.zoom-buttons button').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('.zoom-buttons button').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                loadChartData(parseInt(btn.getAttribute('data-days')));
            });
        });

        // Initial load
        loadComparison();
        loadChartData(30);
    </script>
    <script src="/js/theme.js"></script>
</body>
</html>
