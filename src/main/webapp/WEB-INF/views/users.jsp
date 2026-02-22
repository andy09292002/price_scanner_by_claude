<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Users</title>
    <link rel="stylesheet" href="/css/theme.css">
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background: var(--bg-primary); color: var(--text-primary); }
        table { border-collapse: collapse; width: 100%; }
        th, td { border: 1px solid var(--border-color); padding: 8px; text-align: left; }
        th { background-color: var(--accent); color: white; }
        tr:nth-child(even) { background-color: var(--bg-table-stripe); }
    </style>
</head>
<body>
    <h1>User List</h1>
    <table>
        <thead>
            <tr>
                <th>ID</th>
                <th>Username</th>
                <th>Email</th>
                <th>Active</th>
            </tr>
        </thead>
        <tbody>
            <c:forEach var="user" items="${users}">
                <tr>
                    <td>${user.id}</td>
                    <td>${user.username}</td>
                    <td>${user.email}</td>
                    <td>${user.active ? 'Yes' : 'No'}</td>
                </tr>
            </c:forEach>
        </tbody>
    </table>
    <script src="/js/theme.js"></script>
</body>
</html>
