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

        .price-drop-toggle { display: flex; align-items: center; gap: 6px; }
        .price-drop-toggle button {
            padding: 6px 14px; border: 1px solid var(--accent); background: var(--bg-card);
            color: var(--accent); cursor: pointer; font-size: 14px; border-radius: 4px;
        }
        .price-drop-toggle button.active { background: var(--accent); color: #fff; }

        #pageSizeWrapper { display: flex; align-items: center; gap: 6px; }
        #pageSizeWrapper select {
            padding: 6px 10px; border: 1px solid var(--border-input); border-radius: 4px;
            font-size: 14px; background: var(--input-bg); color: var(--text-primary);
        }

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
        .sub-section summary { background: var(--bg-sub-section); color: var(--text-primary); }

        .product-count { font-size: 13px; font-weight: normal; opacity: 0.9; }

        .category-header {
            padding: 10px 16px; background: var(--bg-sub-section); font-weight: bold;
            font-size: 14px; color: var(--accent-dark); border-top: 1px solid var(--border-color);
        }

        table { border-collapse: collapse; width: 100%; }
        th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid var(--border-color); font-size: 14px; }
        th { background: var(--bg-table-header); color: var(--text-secondary); font-weight: 600; cursor: pointer; user-select: none; }
        th:hover { background: var(--bg-table-header-hover); }
        th[style*="cursor:default"]:hover { background: var(--bg-table-header); }
        th .sort-arrow { font-size: 10px; margin-left: 4px; }
        tr:hover { background: var(--bg-card-hover); }

        .price-regular { color: var(--text-primary); }
        .price-sale { color: var(--danger); font-weight: bold; }
        .price-original-struck { text-decoration: line-through; color: var(--text-muted); font-size: 12px; margin-right: 6px; }
        .discount-badge {
            background: var(--danger); color: #fff; padding: 2px 6px;
            border-radius: 10px; font-size: 11px; font-weight: bold;
        }

        .product-img { width: 40px; height: 40px; object-fit: contain; border-radius: 4px; }
        .no-img { width: 40px; height: 40px; background: var(--bg-no-image); border-radius: 4px; display: inline-block; }
        .product-link { color: inherit; text-decoration: none; }
        .product-link:hover { text-decoration: underline; color: var(--accent); }

        .loading { text-align: center; padding: 40px; color: var(--text-muted); }
        .no-results { text-align: center; padding: 40px; color: var(--text-muted); }

        .flat-table-wrapper { background: var(--bg-card); border-radius: 8px; box-shadow: var(--shadow); overflow: hidden; }

        .pagination-controls {
            display: flex; gap: 4px; align-items: center;
            justify-content: center; padding: 16px; flex-wrap: wrap;
        }
        .pagination-controls button {
            padding: 6px 12px; border: 1px solid var(--border-input);
            background: var(--bg-card); color: var(--text-primary);
            cursor: pointer; border-radius: 4px; font-size: 14px; min-width: 36px;
        }
        .pagination-controls button:hover:not(:disabled) {
            background: var(--accent); color: #fff; border-color: var(--accent);
        }
        .pagination-controls button.active {
            background: var(--accent); color: #fff; border-color: var(--accent);
        }
        .pagination-controls button:disabled { opacity: 0.4; cursor: not-allowed; }
        .page-ellipsis { padding: 6px 4px; color: var(--text-muted); font-size: 14px; }
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
        <div class="price-drop-toggle">
            <label>Price Dropped:</label>
            <button id="priceDrop7">7 Days</button>
            <button id="priceDrop30">30 Days</button>
        </div>
        <div>
            <input type="text" id="searchInput" placeholder="Search products...">
        </div>
        <div class="grouping-toggle">
            <button id="viewFlat" class="active">Flat List</button>
            <button id="groupByStore">By Store</button>
            <button id="groupByCategory">By Category</button>
        </div>
        <div id="pageSizeWrapper">
            <label for="pageSizeSelect">Per page:</label>
            <select id="pageSizeSelect">
                <option value="10" selected>10</option>
                <option value="25">25</option>
                <option value="50">50</option>
            </select>
        </div>
    </div>

    <div class="summary" id="summary"></div>
    <div id="content">
        <div class="loading">Loading products...</div>
    </div>
    <div id="pagination"></div>

    <script>
        let currentData = null;
        let currentView = 'flat';
        let currentSort = { field: 'name', dir: 'asc' };
        let flatSort = { field: 'name', dir: 'asc' };
        let currentPage = 0;
        let currentPageSize = 10;
        let currentPriceDropDays = null;
        let searchTimeout;

        function esc(s) {
            if (s == null) return '';
            return String(s)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;');
        }

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
            if (currentPriceDropDays) params.set('priceDropDays', currentPriceDropDays);

            if (currentView === 'flat') {
                params.set('page', currentPage);
                params.set('size', currentPageSize);
                params.set('sortBy', flatSort.field);
                params.set('sortDir', flatSort.dir);
                const q = document.getElementById('searchInput').value.trim();
                if (q) params.set('search', q);
            } else {
                params.set('groupBy', currentView);
            }

            return params.toString();
        }

        async function fetchData() {
            const content = document.getElementById('content');
            content.innerHTML = '<div class="loading">Loading products...</div>';
            document.getElementById('pagination').innerHTML = '';

            try {
                const qs = buildQueryString();
                const endpoint = currentView === 'flat'
                    ? '/api/products/listing/flat'
                    : '/api/products/listing';
                const resp = await fetch(endpoint + '?' + qs);
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
            html += '<th style="cursor:default">Image</th>';
            html += '<th data-sort="name">Name' + arrow('name') + '</th>';
            html += '<th style="cursor:default">Brand</th>';
            html += '<th style="cursor:default">Size</th>';
            html += '<th data-sort="price">Price' + arrow('price') + '</th>';
            html += '<th data-sort="discount">Discount' + arrow('discount') + '</th>';
            html += '</tr></thead><tbody>';

            for (const p of sorted) {
                const imgHtml = p.imageUrl
                    ? '<img class="product-img" src="' + p.imageUrl + '" loading="lazy" alt="">'
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
                const detailUrl = '/products/' + p.productId + (storeId ? '?storeId=' + storeId : '');

                html += '<tr>';
                html += '<td>' + imgHtml + '</td>';
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

        function renderFlatData() {
            const content = document.getElementById('content');
            const summary = document.getElementById('summary');

            if (!currentData || !currentData.items) {
                content.innerHTML = '<div class="no-results">No data available</div>';
                summary.textContent = '';
                renderPagination();
                return;
            }

            const d = currentData;
            if (d.totalItems > 0) {
                const from = d.page * d.size + 1;
                const to = Math.min(from + d.items.length - 1, d.totalItems);
                summary.textContent = 'Showing ' + from + '\u2013' + to + ' of ' + d.totalItems + ' products';
            } else {
                summary.textContent = 'No products found';
            }

            if (d.items.length === 0) {
                content.innerHTML = '<div class="no-results">No products found matching your filters.</div>';
                renderPagination();
                return;
            }

            const arrow = (field) => flatSort.field === field
                ? '<span class="sort-arrow">' + (flatSort.dir === 'asc' ? '\u25B2' : '\u25BC') + '</span>' : '';

            let html = '<div class="flat-table-wrapper"><table>';
            html += '<thead><tr>';
            html += '<th style="cursor:default">Image</th>';
            html += '<th data-flat-sort="name">Name' + arrow('name') + '</th>';
            html += '<th style="cursor:default">Brand</th>';
            html += '<th style="cursor:default">Size</th>';
            html += '<th data-flat-sort="store">Store' + arrow('store') + '</th>';
            html += '<th data-flat-sort="category">Category' + arrow('category') + '</th>';
            html += '<th data-flat-sort="price">Price' + arrow('price') + '</th>';
            html += '<th data-flat-sort="discount">Discount' + arrow('discount') + '</th>';
            html += '</tr></thead><tbody>';

            for (const p of d.items) {
                const imgHtml = p.imageUrl
                    ? '<img class="product-img" src="' + esc(p.imageUrl) + '" loading="lazy" alt="">'
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
                const detailUrl = '/products/' + esc(p.productId) + (p.storeId ? '?storeId=' + esc(p.storeId) : '');

                html += '<tr>';
                html += '<td>' + imgHtml + '</td>';
                html += '<td><a class="product-link" href="' + detailUrl + '">' + esc(p.name) + '</a></td>';
                html += '<td>' + esc(p.brand) + '</td>';
                html += '<td>' + esc(sizeText) + '</td>';
                html += '<td>' + esc(p.storeName) + '</td>';
                html += '<td>' + esc(p.categoryName) + '</td>';
                html += '<td>' + priceHtml + '</td>';
                html += '<td>' + discountHtml + '</td>';
                html += '</tr>';
            }

            html += '</tbody></table></div>';
            content.innerHTML = html;

            content.querySelectorAll('th[data-flat-sort]').forEach(th => {
                th.addEventListener('click', () => {
                    const field = th.getAttribute('data-flat-sort');
                    if (flatSort.field === field) {
                        flatSort.dir = flatSort.dir === 'asc' ? 'desc' : 'asc';
                    } else {
                        flatSort.field = field;
                        flatSort.dir = 'asc';
                    }
                    currentPage = 0;
                    fetchData();
                });
            });

            renderPagination();
        }

        function renderPagination() {
            const paginationDiv = document.getElementById('pagination');
            if (currentView !== 'flat' || !currentData || currentData.totalPages <= 1) {
                paginationDiv.innerHTML = '';
                return;
            }

            const { page, totalPages } = currentData;
            const range = getPaginationRange(page, totalPages);

            let html = '<div class="pagination-controls">';
            html += '<button onclick="goToPage(' + (page - 1) + ')"' + (page === 0 ? ' disabled' : '') + '>\u2039 Prev</button>';

            for (const p of range) {
                if (p === '...') {
                    html += '<span class="page-ellipsis">\u2026</span>';
                } else {
                    html += '<button onclick="goToPage(' + p + ')"' + (p === page ? ' class="active"' : '') + '>' + (p + 1) + '</button>';
                }
            }

            html += '<button onclick="goToPage(' + (page + 1) + ')"' + (page >= totalPages - 1 ? ' disabled' : '') + '>Next \u203a</button>';
            html += '</div>';
            paginationDiv.innerHTML = html;
        }

        function getPaginationRange(current, total) {
            if (total <= 7) return Array.from({ length: total }, (_, i) => i);
            const range = [0];
            const left = Math.max(1, current - 2);
            const right = Math.min(total - 2, current + 2);
            if (left > 1) range.push('...');
            for (let i = left; i <= right; i++) range.push(i);
            if (right < total - 2) range.push('...');
            range.push(total - 1);
            return range;
        }

        function goToPage(p) {
            if (!currentData || p < 0 || p >= currentData.totalPages) return;
            currentPage = p;
            fetchData();
        }

        function renderData() {
            const content = document.getElementById('content');
            const summary = document.getElementById('summary');

            if (!currentData) {
                content.innerHTML = '<div class="no-results">No data available</div>';
                summary.textContent = '';
                return;
            }

            if (currentView === 'flat') {
                renderFlatData();
                return;
            }

            if (currentView === 'store') {
                renderByStore(currentData, content, summary);
            } else {
                renderByCategory(currentData, content, summary);
            }

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

            renderPagination();
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

        function updateViewButtons() {
            document.getElementById('viewFlat').classList.toggle('active', currentView === 'flat');
            document.getElementById('groupByStore').classList.toggle('active', currentView === 'store');
            document.getElementById('groupByCategory').classList.toggle('active', currentView === 'category');
        }

        function updatePageSizeVisibility() {
            document.getElementById('pageSizeWrapper').style.display = currentView === 'flat' ? '' : 'none';
        }

        function filterCategoriesByStore() {
            const selectedStoreIds = getSelectedValues('storeFilter');
            const catSelect = document.getElementById('categoryFilter');
            catSelect.querySelectorAll('option').forEach(opt => {
                const storeId = opt.getAttribute('data-store-id');
                if (selectedStoreIds.length === 0) {
                    opt.style.display = '';
                } else {
                    opt.style.display = selectedStoreIds.includes(storeId) ? '' : 'none';
                    if (!selectedStoreIds.includes(storeId) && opt.selected) opt.selected = false;
                }
            });
        }

        function togglePriceDrop(days) {
            currentPriceDropDays = currentPriceDropDays === days ? null : days;
            document.getElementById('priceDrop7').classList.toggle('active', currentPriceDropDays === 7);
            document.getElementById('priceDrop30').classList.toggle('active', currentPriceDropDays === 30);
            currentPage = 0;
            fetchData();
        }

        // Event listeners
        document.getElementById('storeFilter').addEventListener('change', () => {
            filterCategoriesByStore();
            currentPage = 0;
            fetchData();
        });
        document.getElementById('categoryFilter').addEventListener('change', () => { currentPage = 0; fetchData(); });
        document.getElementById('onSaleOnly').addEventListener('change', () => { currentPage = 0; fetchData(); });

        document.getElementById('searchInput').addEventListener('input', () => {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => {
                if (currentView === 'flat') {
                    currentPage = 0;
                    fetchData();
                } else if (currentData) {
                    renderData();
                }
            }, 300);
        });

        document.getElementById('viewFlat').addEventListener('click', () => {
            currentView = 'flat';
            currentPage = 0;
            updateViewButtons();
            updatePageSizeVisibility();
            fetchData();
        });

        document.getElementById('groupByStore').addEventListener('click', () => {
            currentView = 'store';
            updateViewButtons();
            updatePageSizeVisibility();
            fetchData();
        });

        document.getElementById('groupByCategory').addEventListener('click', () => {
            currentView = 'category';
            updateViewButtons();
            updatePageSizeVisibility();
            fetchData();
        });

        document.getElementById('pageSizeSelect').addEventListener('change', () => {
            currentPageSize = parseInt(document.getElementById('pageSizeSelect').value);
            currentPage = 0;
            fetchData();
        });

        document.getElementById('priceDrop7').addEventListener('click', () => togglePriceDrop(7));
        document.getElementById('priceDrop30').addEventListener('click', () => togglePriceDrop(30));

        // Initial load
        fetchData();
    </script>
    <script src="/js/theme.js"></script>
</body>
</html>