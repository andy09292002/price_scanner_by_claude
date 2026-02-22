<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Product Price Listing</title>
    <link rel="stylesheet" href="/css/theme.css">
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background: var(--bg-primary); color: var(--text-primary); }
        h1 { color: var(--text-primary); }

        .filter-bar {
            background: var(--bg-card); padding: 16px; border-radius: 8px;
            margin-bottom: 20px; display: flex; flex-wrap: wrap; gap: 12px;
            align-items: center; box-shadow: var(--shadow);
        }
        .filter-bar label { font-weight: bold; font-size: 14px; color: var(--text-primary); }
        .filter-bar select, .filter-bar input[type="text"] {
            padding: 6px 10px; border: 1px solid var(--border-input); border-radius: 4px; font-size: 14px;
            background: var(--input-bg); color: var(--text-primary);
        }
        .filter-bar select { min-width: 160px; }
        .filter-bar input[type="text"] { min-width: 200px; }
        .filter-bar .checkbox-group { display: flex; align-items: center; gap: 4px; }

        .grouping-toggle { display: flex; gap: 0; }
        .grouping-toggle button {
            padding: 6px 14px; border: 1px solid var(--accent); background: var(--bg-card);
            color: var(--accent); cursor: pointer; font-size: 14px;
        }
        .grouping-toggle button:first-child { border-radius: 4px 0 0 4px; }
        .grouping-toggle button:last-child { border-radius: 0 4px 4px 0; }
        .grouping-toggle button.active { background: var(--accent); color: #fff; }

        .summary { margin-bottom: 16px; color: var(--text-summary); font-size: 14px; }

        .store-section, .category-section {
            background: var(--bg-card); border-radius: 8px; margin-bottom: 12px;
            box-shadow: var(--shadow); overflow: hidden;
        }
        .store-section summary, .category-section summary {
            padding: 12px 16px; cursor: pointer; font-size: 16px; font-weight: bold;
            background: var(--accent); color: #fff; list-style: none; display: flex;
            justify-content: space-between; align-items: center;
        }
        .store-section summary::-webkit-details-marker,
        .category-section summary::-webkit-details-marker { display: none; }
        .store-section summary::after, .category-section summary::after {
            content: '\25B6'; font-size: 12px; transition: transform 0.2s;
        }
        .store-section[open] summary::after, .category-section[open] summary::after {
            transform: rotate(90deg);
        }
        .sub-section summary {
            background: var(--bg-sub-section); color: var(--text-primary);
        }

        .product-count { font-size: 13px; font-weight: normal; opacity: 0.9; }

        .category-header {
            padding: 10px 16px; background: var(--bg-sub-section); font-weight: bold;
            font-size: 14px; color: var(--accent-dark); border-top: 1px solid var(--border-color);
        }

        table { border-collapse: collapse; width: 100%; }
        th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid var(--border-color); font-size: 14px; }
        th { background: var(--bg-table-header); color: var(--text-secondary); font-weight: 600; cursor: pointer; user-select: none; }
        th:hover { background: var(--bg-table-header-hover); }
        th .sort-arrow { font-size: 10px; margin-left: 4px; }
        tr:hover { background: var(--bg-card-hover); }

        .price-regular { color: var(--text-primary); }
        .price-sale { color: var(--danger); font-weight: bold; }
        .price-original-struck { text-decoration: line-through; color: var(--text-muted); font-size: 12px; margin-right: 6px; }
        .discount-badge {
            background: var(--danger); color: #fff; padding: 2px 6px;
            border-radius: 10px; font-size: 11px; font-weight: bold;
        }
        .on-sale-tag {
            background: var(--warning); color: #fff; padding: 2px 6px;
            border-radius: 10px; font-size: 11px;
        }

        .product-img { width: 40px; height: 40px; object-fit: contain; border-radius: 4px; }
        .no-img { width: 40px; height: 40px; background: var(--bg-no-image); border-radius: 4px; display: inline-block; }
        .product-link { color: inherit; text-decoration: none; }
        .product-link:hover { text-decoration: underline; color: var(--accent); }

        .loading { text-align: center; padding: 40px; color: var(--text-muted); }
        .no-results { text-align: center; padding: 40px; color: var(--text-muted); }
    </style>
</head>
<body>
    <h1>Product Price Listing</h1>

    <div class="filter-bar">
        <div>
            <label for="storeFilter">Store:</label>
            <select id="storeFilter" multiple>
                <c:forEach var="store" items="${stores}">
                    <option value="${store.id}">${store.name}</option>
                </c:forEach>
            </select>
        </div>
        <div>
            <label for="categoryFilter">Category:</label>
            <select id="categoryFilter" multiple>
                <c:forEach var="category" items="${categories}">
                    <option value="${category.id}" data-store-id="${category.storeId}">${category.name}</option>
                </c:forEach>
            </select>
        </div>
        <div class="checkbox-group">
            <input type="checkbox" id="onSaleOnly">
            <label for="onSaleOnly">On Sale Only</label>
        </div>
        <div>
            <input type="text" id="searchInput" placeholder="Search products...">
        </div>
        <div class="grouping-toggle">
            <button id="groupByStore" class="active">By Store</button>
            <button id="groupByCategory">By Category</button>
        </div>
    </div>

    <div class="summary" id="summary"></div>
    <div id="content">
        <div class="loading">Loading products...</div>
    </div>

    <script>
        let currentData = null;
        let currentGroupBy = 'store';
        let currentSort = { field: 'name', dir: 'asc' };

        function getSelectedValues(selectId) {
            const select = document.getElementById(selectId);
            return Array.from(select.selectedOptions).map(o => o.value);
        }

        function buildQueryString() {
            const params = new URLSearchParams();
            const storeIds = getSelectedValues('storeFilter');
            const categoryIds = getSelectedValues('categoryFilter');
            const onSaleOnly = document.getElementById('onSaleOnly').checked;

            if (storeIds.length > 0) params.set('storeIds', storeIds.join(','));
            if (categoryIds.length > 0) params.set('categoryIds', categoryIds.join(','));
            if (onSaleOnly) params.set('onSaleOnly', 'true');
            params.set('groupBy', currentGroupBy);

            return params.toString();
        }

        async function fetchData() {
            const content = document.getElementById('content');
            content.innerHTML = '<div class="loading">Loading products...</div>';

            try {
                const qs = buildQueryString();
                const resp = await fetch('/api/products/listing?' + qs);
                if (!resp.ok) throw new Error('Failed to fetch');
                currentData = await resp.json();
                renderData();
            } catch (e) {
                content.innerHTML = '<div class="no-results">Error loading products. Please try again.</div>';
            }
        }

        function formatPrice(price) {
            if (price == null) return '-';
            return '$' + parseFloat(price).toFixed(2);
        }

        function sortProducts(products) {
            const sorted = [...products];
            sorted.sort((a, b) => {
                let va, vb;
                switch (currentSort.field) {
                    case 'name': va = (a.name || '').toLowerCase(); vb = (b.name || '').toLowerCase(); break;
                    case 'price':
                        va = a.onSale && a.salePrice != null ? parseFloat(a.salePrice) : parseFloat(a.regularPrice || 0);
                        vb = b.onSale && b.salePrice != null ? parseFloat(b.salePrice) : parseFloat(b.regularPrice || 0);
                        break;
                    case 'discount': va = a.discountPercent || 0; vb = b.discountPercent || 0; break;
                    default: va = a.name; vb = b.name;
                }
                if (va < vb) return currentSort.dir === 'asc' ? -1 : 1;
                if (va > vb) return currentSort.dir === 'asc' ? 1 : -1;
                return 0;
            });
            return sorted;
        }

        function filterBySearch(products) {
            const q = document.getElementById('searchInput').value.toLowerCase().trim();
            if (!q) return products;
            return products.filter(p =>
                (p.name && p.name.toLowerCase().includes(q)) ||
                (p.brand && p.brand.toLowerCase().includes(q))
            );
        }

        function renderProductTable(products, storeId) {
            const filtered = filterBySearch(products);
            const sorted = sortProducts(filtered);
            if (sorted.length === 0) return '<p style="padding:8px 16px;color:var(--text-muted);">No products found</p>';

            const arrow = (field) => currentSort.field === field
                ? '<span class="sort-arrow">' + (currentSort.dir === 'asc' ? '\u25B2' : '\u25BC') + '</span>' : '';

            let html = '<table>';
            html += '<thead><tr>';
            html += '<th>Image</th>';
            html += '<th data-sort="name">Name' + arrow('name') + '</th>';
            html += '<th>Brand</th>';
            html += '<th>Size</th>';
            html += '<th data-sort="price">Price' + arrow('price') + '</th>';
            html += '<th data-sort="discount">Discount' + arrow('discount') + '</th>';
            html += '</tr></thead><tbody>';

            for (const p of sorted) {
                const imgHtml = p.imageUrl
                    ? '<img class="product-img" src="' + p.imageUrl + '" alt="">'
                    : '<span class="no-img"></span>';

                let priceHtml;
                if (p.onSale && p.salePrice != null) {
                    priceHtml = '<span class="price-original-struck">' + formatPrice(p.regularPrice) + '</span>'
                        + '<span class="price-sale">' + formatPrice(p.salePrice) + '</span>';
                } else {
                    priceHtml = '<span class="price-regular">' + formatPrice(p.regularPrice) + '</span>';
                }

                let discountHtml = '';
                if (p.onSale && p.discountPercent > 0) {
                    discountHtml = '<span class="discount-badge">-' + p.discountPercent.toFixed(1) + '%</span>';
                }

                const sizeText = [p.size, p.unit].filter(Boolean).join(' ');

                html += '<tr>';
                html += '<td>' + imgHtml + '</td>';
                const detailUrl = '/products/' + p.productId + (storeId ? '?storeId=' + storeId : '');
                html += '<td><a class="product-link" href="' + detailUrl + '">' + (p.name || '') + '</a></td>';
                html += '<td>' + (p.brand || '') + '</td>';
                html += '<td>' + sizeText + '</td>';
                html += '<td>' + priceHtml + '</td>';
                html += '<td>' + discountHtml + '</td>';
                html += '</tr>';
            }

            html += '</tbody></table>';
            return html;
        }

        function renderData() {
            const content = document.getElementById('content');
            const summary = document.getElementById('summary');

            if (!currentData) {
                content.innerHTML = '<div class="no-results">No data available</div>';
                summary.textContent = '';
                return;
            }

            if (currentGroupBy === 'store') {
                renderByStore(currentData, content, summary);
            } else {
                renderByCategory(currentData, content, summary);
            }

            // Attach sort handlers
            content.querySelectorAll('th[data-sort]').forEach(th => {
                th.addEventListener('click', () => {
                    const field = th.getAttribute('data-sort');
                    if (currentSort.field === field) {
                        currentSort.dir = currentSort.dir === 'asc' ? 'desc' : 'asc';
                    } else {
                        currentSort.field = field;
                        currentSort.dir = 'asc';
                    }
                    renderData();
                });
            });
        }

        function renderByStore(data, content, summary) {
            summary.textContent = data.totalProducts + ' products across ' + data.storeCount + ' stores';

            if (!data.groups || data.groups.length === 0) {
                content.innerHTML = '<div class="no-results">No products found matching your filters.</div>';
                return;
            }

            let html = '';
            for (const store of data.groups) {
                html += '<details class="store-section" open>';
                html += '<summary>' + store.storeName + ' <span class="product-count">(' + store.productCount + ' products)</span></summary>';

                for (const cat of store.categories) {
                    html += '<div class="category-header">' + cat.categoryName + '</div>';
                    html += renderProductTable(cat.products, store.storeId);
                }

                html += '</details>';
            }
            content.innerHTML = html;
        }

        function renderByCategory(data, content, summary) {
            summary.textContent = data.totalProducts + ' products across ' + data.categoryCount + ' categories';

            if (!data.groups || data.groups.length === 0) {
                content.innerHTML = '<div class="no-results">No products found matching your filters.</div>';
                return;
            }

            let html = '';
            for (const cat of data.groups) {
                html += '<details class="category-section" open>';
                html += '<summary>' + cat.categoryName + ' <span class="product-count">(' + cat.productCount + ' products)</span></summary>';

                for (const store of cat.stores) {
                    html += '<div class="category-header">' + store.storeName + '</div>';
                    html += renderProductTable(store.products, store.storeId);
                }

                html += '</details>';
            }
            content.innerHTML = html;
        }

        function filterCategoriesByStore() {
            const selectedStoreIds = getSelectedValues('storeFilter');
            const catSelect = document.getElementById('categoryFilter');
            const options = catSelect.querySelectorAll('option');

            options.forEach(opt => {
                const storeId = opt.getAttribute('data-store-id');
                if (selectedStoreIds.length === 0) {
                    opt.style.display = '';
                } else {
                    opt.style.display = selectedStoreIds.includes(storeId) ? '' : 'none';
                    if (!selectedStoreIds.includes(storeId) && opt.selected) {
                        opt.selected = false;
                    }
                }
            });
        }

        // Event listeners
        document.getElementById('storeFilter').addEventListener('change', () => {
            filterCategoriesByStore();
            fetchData();
        });
        document.getElementById('categoryFilter').addEventListener('change', fetchData);
        document.getElementById('onSaleOnly').addEventListener('change', fetchData);

        let searchTimeout;
        document.getElementById('searchInput').addEventListener('input', () => {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => { if (currentData) renderData(); }, 300);
        });

        document.getElementById('groupByStore').addEventListener('click', () => {
            currentGroupBy = 'store';
            document.getElementById('groupByStore').classList.add('active');
            document.getElementById('groupByCategory').classList.remove('active');
            fetchData();
        });

        document.getElementById('groupByCategory').addEventListener('click', () => {
            currentGroupBy = 'category';
            document.getElementById('groupByCategory').classList.add('active');
            document.getElementById('groupByStore').classList.remove('active');
            fetchData();
        });

        // Initial load
        fetchData();
    </script>
    <script src="/js/theme.js"></script>
</body>
</html>
