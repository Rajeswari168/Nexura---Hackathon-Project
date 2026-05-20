// Nexura Alerts & Anomalies Portal Engine
let allAlerts = [];
let currentFilter = "active"; // "active", "archived", "all"

document.addEventListener("DOMContentLoaded", () => {
    // 1. Render Sidebar nav template
    renderSidebar("alerts");

    // 2. Load Profile Info
    loadUserProfile();

    // 3. Fetch Alerts
    fetchAlerts();

    // 4. Fetch AI Risk banner overview
    fetchRiskBanner();
});

async function loadUserProfile() {
    try {
        const resp = await fetchWithAuth("/api/auth/me");
        if (!resp) return;
        const user = await resp.json();

        // Update profile widget
        document.getElementById("profile-name").innerText = user.fullName;
        const avatar = user.fullName.split(" ").map(n => n[0]).join("").substring(0, 2).toUpperCase();
        document.getElementById("user-avatar").innerText = avatar;
    } catch (err) {
        console.error("Error loading user profile:", err);
    }
}

async function fetchRiskBanner() {
    try {
        const resp = await fetchWithAuth("/api/ai/risk-status");
        if (!resp) return;
        const data = await resp.json();

        const bannerContainer = document.getElementById("risk-overview-container");
        if (!bannerContainer) return;

        // Render small beautiful bar if risk is not low or high
        bannerContainer.className = `risk-banner risk-${data.riskLevel}`;
        bannerContainer.style.display = "flex";
        bannerContainer.style.margin = "0 0 24px 0";
        bannerContainer.innerHTML = `
            <div style="display:flex; align-items:center; gap:12px;">
                <span style="font-size:24px;">🚨</span>
                <div>
                    <h4 style="font-weight:700; margin-bottom:2px;">AI Diagnostics Status: ${data.riskLevel} Risk</h4>
                    <p style="font-size:13px; opacity:0.9;">${data.keyFindings}</p>
                </div>
            </div>
            <a href="dashboard.html" class="btn btn-secondary" style="font-size:12px; padding:6px 12px; border-color:rgba(0,0,0,0.15); background:rgba(255,255,255,0.25);">Dashboard Overview</a>
        `;
    } catch (err) {
        console.error("Error loading AI Risk status:", err);
    }
}

async function fetchAlerts() {
    const container = document.getElementById("alerts-stream-container");
    try {
        const resp = await fetchWithAuth("/api/alerts");
        if (!resp) {
            container.innerHTML = `<div class="card" style="padding:24px; text-align:center; color:var(--text-danger);">Authentication required. Please log in again.</div>`;
            return;
        }
        allAlerts = await resp.json();
        
        // Render
        renderAlerts();
    } catch (err) {
        container.innerHTML = `<div class="card" style="padding:24px; text-align:center; color:var(--text-danger);">Failed to retrieve alerts stream from backend.</div>`;
    }
}

function setAlertFilter(filter) {
    currentFilter = filter;
    
    // Toggle active tab classes
    const tabs = document.querySelectorAll(".filter-tab");
    tabs.forEach(tab => {
        tab.classList.remove("active");
        if (tab.innerText.toLowerCase().includes(filter)) {
            tab.classList.add("active");
        }
    });

    renderAlerts();
}

function renderAlerts() {
    const container = document.getElementById("alerts-stream-container");
    container.innerHTML = "";

    // Filter alerts
    let filtered = [];
    if (currentFilter === "active") {
        filtered = allAlerts.filter(a => !a.isRead);
    } else if (currentFilter === "archived") {
        filtered = allAlerts.filter(a => a.isRead);
    } else {
        filtered = allAlerts;
    }

    // Update active alerts count count
    const activeCount = allAlerts.filter(a => !a.isRead).length;
    document.getElementById("active-count").innerText = activeCount;

    // Toggle mark all as read button
    const markAllBtn = document.getElementById("mark-all-read-btn");
    if (activeCount === 0) {
        markAllBtn.style.display = "none";
    } else {
        markAllBtn.style.display = "inline-flex";
    }

    if (filtered.length === 0) {
        let msg = "You are all caught up! No active warnings present.";
        let icon = "☀️";
        if (currentFilter === "archived") {
            msg = "No archived logs. Keep a clean record!";
            icon = "📁";
        } else if (currentFilter === "all") {
            msg = "No warnings ever recorded in Nexura database.";
            icon = "🌈";
        }

        container.innerHTML = `
            <div class="empty-alerts-placeholder">
                <div class="empty-icon">${icon}</div>
                <h3>${msg}</h3>
                <p style="margin-top: 8px; font-size:14px;">Physiological thresholds are normal and compliance sequences are secure.</p>
            </div>
        `;
        return;
    }

    filtered.forEach(alert => {
        const card = document.createElement("div");
        const priorityClass = alert.priority.toLowerCase(); // critical, high, medium, low
        const readClass = alert.isRead ? "read" : "";
        card.className = `alert-card ${priorityClass} ${readClass}`;
        card.id = `alert-card-${alert.id}`;

        // Get category label & icon
        let catText = "AI Analytics Warning";
        let catIcon = "🤖";
        if (alert.category === "MEDICATION_MISSED") {
            catText = "Missed Medication Dose";
            catIcon = "💊";
        } else if (alert.category === "SLEEP_DETERIORATION") {
            catText = "Sleep Trend Deterioration";
            catIcon = "🌙";
        } else if (alert.category === "ABNORMAL_VITALS") {
            catText = "Vital Sign Degradation";
            catIcon = "❤️";
        } else if (alert.category === "CRITICAL_EMERGENCY") {
            catText = "Critical Care Escalation";
            catIcon = "🚨";
        }

        // Format dates beautifully
        const dateObj = new Date(alert.createdAt);
        const formattedDate = dateObj.toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' }) + 
                              " at " + 
                              dateObj.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

        // Action button if unread
        const actionHtml = !alert.isRead ? `
            <button class="btn btn-secondary" onclick="markSingleRead(${alert.id})" style="padding: 8px 14px; font-size: 13px; font-weight:600; display:flex; align-items:center; gap:4px;">
                <span>✓</span> Dismiss
            </button>
        ` : `
            <span style="font-size: 13px; color: var(--text-muted); font-weight:500; display:flex; align-items:center; gap:4px;">
                <span>✓</span> Archived
            </span>
        `;

        card.innerHTML = `
            <div class="alert-info-col">
                <div class="alert-meta">
                    <span class="badge badge-${priorityClass}">${alert.priority} Priority</span>
                    <span class="badge badge-category">${catIcon} ${catText}</span>
                    <span class="alert-time">⏱️ ${formattedDate}</span>
                </div>
                <div class="alert-title-row" style="margin-top: 6px;">
                    <h3 class="alert-card-title">${alert.title}</h3>
                </div>
                <p class="alert-desc">${alert.message}</p>
            </div>
            <div class="alert-actions">
                ${actionHtml}
            </div>
        `;
        container.appendChild(card);
    });
}

async function markSingleRead(id) {
    try {
        const resp = await fetchWithAuth(`/api/alerts/${id}/read`, { method: "PUT" });
        if (resp && resp.ok) {
            showToast("Alert dismissed.", "success");
            
            // Animating locally
            const element = document.getElementById(`alert-card-${id}`);
            if (element) {
                element.style.transform = "translateX(50px)";
                element.style.opacity = "0";
                
                // Wait for animation to finish
                setTimeout(() => {
                    // Update state
                    const idx = allAlerts.findIndex(a => a.id === id);
                    if (idx !== -1) {
                        allAlerts[idx].isRead = true;
                    }
                    renderAlerts();
                }, 300);
            } else {
                fetchAlerts();
            }
        }
    } catch (err) {
        showToast("Failed to modify alert status.", "error");
    }
}

async function markAllAlertsAsRead() {
    const unread = allAlerts.filter(a => !a.isRead);
    if (unread.length === 0) return;

    try {
        showToast("Dismissing all active alerts...", "info");
        
        // Trigger all requests concurrently
        const requests = unread.map(a => fetchWithAuth(`/api/alerts/${a.id}/read`, { method: "PUT" }));
        await Promise.all(requests);
        
        showToast("All active alerts dismissed successfully.", "success");
        
        // Refresh alerts list
        fetchAlerts();
    } catch (err) {
        showToast("Failed to dismiss some alerts. Please try again.", "error");
        fetchAlerts();
    }
}
