// Nexura Global Javascript Utility

const API_BASE = "http://localhost:8080";

// 1. Theme Manager
function initTheme() {
    const savedTheme = localStorage.getItem("nexura-theme") || "light";
    document.documentElement.setAttribute("data-theme", savedTheme);
    updateThemeButtonText(savedTheme);
}

function toggleTheme() {
    const currentTheme = document.documentElement.getAttribute("data-theme");
    const newTheme = currentTheme === "dark" ? "light" : "dark";
    document.documentElement.setAttribute("data-theme", newTheme);
    localStorage.setItem("nexura-theme", newTheme);
    updateThemeButtonText(newTheme);
}

function updateThemeButtonText(theme) {
    const btn = document.getElementById("theme-toggle");
    if (btn) {
        btn.innerHTML = theme === "dark" 
            ? '<span class="icon">☀️</span> <span class="nav-text">Light Mode</span>' 
            : '<span class="icon">🌙</span> <span class="nav-text">Dark Mode</span>';
    }
}

// 2. Authentication Utilities
function getAuthToken() {
    return localStorage.getItem("nexura-token");
}

function setSession(token, email, fullName) {
    localStorage.setItem("nexura-token", token);
    localStorage.setItem("nexura-email", email);
    localStorage.setItem("nexura-name", fullName);
}

function clearSession() {
    localStorage.removeItem("nexura-token");
    localStorage.removeItem("nexura-email");
    localStorage.removeItem("nexura-name");
    window.location.href = "login.html";
}

function isAuthenticated() {
    return !!getAuthToken();
}

// 3. Routing guards
function checkRouteAuth() {
    const isAuthPage = window.location.pathname.includes("login.html") || window.location.pathname.includes("register.html");
    if (!isAuthenticated() && !isAuthPage) {
        window.location.href = "login.html";
    } else if (isAuthenticated() && isAuthPage) {
        window.location.href = "dashboard.html";
    }
}

// 4. Secure Fetch Wrapper with Auth Injector
async function fetchWithAuth(endpoint, options = {}) {
    const token = getAuthToken();
    const headers = {
        "Content-Type": "application/json",
        ...options.headers
    };

    if (token) {
        headers["Authorization"] = `Bearer ${token}`;
    }

    const config = {
        ...options,
        headers
    };

    try {
        const response = await fetch(`${API_BASE}${endpoint}`, config);
        
        if (response.status === 401) {
            // Unauthorized - token expired or invalid
            clearSession();
            return null;
        }
        
        return response;
    } catch (err) {
        showToast("Server connection error. Please try again.", "error");
        throw err;
    }
}

// 5. Global Toast Notification Generator
function showToast(message, type = "success") {
    let container = document.getElementById("toast-container");
    if (!container) {
        container = document.createElement("div");
        container.id = "toast-container";
        container.className = "toast-container";
        document.body.appendChild(container);
    }

    const toast = document.createElement("div");
    toast.className = `toast toast-${type}`;
    
    let icon = "ℹ️";
    if (type === "success") icon = "✅";
    if (type === "warning") icon = "⚠️";
    if (type === "error") icon = "❌";

    // Set custom visual properties based on notification type
    if (type === "success") toast.style.borderLeft = "4px solid var(--success)";
    if (type === "warning") toast.style.borderLeft = "4px solid var(--warning)";
    if (type === "error") toast.style.borderLeft = "4px solid var(--danger)";

    toast.innerHTML = `
        <span class="toast-icon">${icon}</span>
        <span class="toast-message" style="font-size:14px; font-weight:500; color:var(--text-secondary);">${message}</span>
    `;

    container.appendChild(toast);

    // Auto dismiss after 4 seconds
    setTimeout(() => {
        toast.style.animation = "slideOut 0.3s ease-in forwards";
        setTimeout(() => {
            toast.remove();
        }, 300);
    }, 4000);
}

// 6. Navigation Renderer (Bypasses duplicate HTML boilerplate!)
function renderSidebar(activePage) {
    const sidebarContainer = document.getElementById("sidebar-container");
    if (!sidebarContainer) return;

    const email = localStorage.getItem("nexura-email") || "";
    const name = localStorage.getItem("nexura-name") || "Patient";
    const initials = name.split(" ").map(n => n[0]).join("").substring(0, 2).toUpperCase();

    sidebarContainer.innerHTML = `
        <div class="sidebar">
            <div class="logo-container">
                <div class="logo-icon">N</div>
                <span class="logo-text">Nexura</span>
            </div>
            
            <ul class="nav-links">
                <li class="nav-item ${activePage === 'dashboard' ? 'active' : ''}"><a href="dashboard.html"><span>📊</span><span class="nav-text">Dashboard</span></a></li>
                <li class="nav-item ${activePage === 'health' ? 'active' : ''}"><a href="health.html"><span>❤️</span><span class="nav-text">Health Logs</span></a></li>
                <li class="nav-item ${activePage === 'sleep' ? 'active' : ''}"><a href="sleep.html"><span>🌙</span><span class="nav-text">Sleep Tracking</span></a></li>
                <li class="nav-item ${activePage === 'medications' ? 'active' : ''}"><a href="medications.html"><span>💊</span><span class="nav-text">Medications</span></a></li>
                <li class="nav-item ${activePage === 'reports' ? 'active' : ''}"><a href="reports.html"><span>📂</span><span class="nav-text">Medical Reports</span></a></li>
                <li class="nav-item ${activePage === 'chatbot' ? 'active' : ''}"><a href="chatbot.html"><span>🤖</span><span class="nav-text">AI Chatbot</span></a></li>
                <li class="nav-item ${activePage === 'alerts' ? 'active' : ''}"><a href="alerts.html"><span>🔔</span><span class="nav-text">Alerts</span></a></li>
                <li class="nav-item ${activePage === 'emergency' ? 'active' : ''}"><a href="emergency.html"><span>🚨</span><span class="nav-text">Emergency Support</span></a></li>
            </ul>

            <div class="sidebar-footer">
                <button id="theme-toggle" class="theme-toggle-btn" onclick="toggleTheme()">
                    ☀️ Light Mode
                </button>
                <div class="nav-item" style="margin-top: 12px;">
                    <a href="#" onclick="clearSession()" style="color:var(--danger);">
                        <span>🚪</span><span class="nav-text">Logout</span>
                    </a>
                </div>
            </div>
        </div>
    `;
    
    initTheme();
}

// Check route guards instantly
checkRouteAuth();
