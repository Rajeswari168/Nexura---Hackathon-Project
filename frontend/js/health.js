// Nexura Health Logs Engine
let detailedChartInstance = null;

document.addEventListener("DOMContentLoaded", () => {
    // 1. Render Navigation Template
    renderSidebar("health");

    // 2. Load Patient Data
    loadHealthPortal();
});

async function loadHealthPortal() {
    try {
        // Fetch User Info
        const userResp = await fetchWithAuth("/api/auth/me");
        if (!userResp) return;
        const user = await userResp.json();

        // Update profile widget header
        document.getElementById("profile-name").innerText = user.fullName;
        const avatar = user.fullName.split(" ").map(n => n[0]).join("").substring(0, 2).toUpperCase();
        document.getElementById("user-avatar").innerText = avatar;

        // Fetch logs
        await fetchHealthHistory();
    } catch (err) {
        // Graceful
    }
}

async function fetchHealthHistory() {
    try {
        const resp = await fetchWithAuth("/api/health/history");
        if (!resp) return;
        const data = await resp.json();

        renderHistoryTable(data);
        renderDetailedChart(data);
    } catch (err) {
        showToast("Error retrieving health logs history.", "error");
    }
}

function renderHistoryTable(logs) {
    const tbody = document.getElementById("vitals-history-rows");
    tbody.innerHTML = "";

    if (logs.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8" style="text-align: center; color: var(--text-muted); padding: 40px 0;">
                    No health vitals logged yet. Use the logger on the left to record your first entry.
                </td>
            </tr>
        `;
        return;
    }

    logs.forEach(log => {
        const tr = document.createElement("tr");
        const formattedDate = new Date(log.createdAt).toLocaleString([], {
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });

        // Set visual pill styling based on vitals threshold triggers
        let o2Style = "font-weight:600; color:var(--success);";
        if (log.oxygenLevel < 90) {
            o2Style = "font-weight:700; color:var(--danger); background-color:var(--danger-light); padding:4px 8px; border-radius:4px;";
        } else if (log.oxygenLevel < 95) {
            o2Style = "font-weight:600; color:var(--warning);";
        }

        let hrStyle = "font-weight:600;";
        if (log.heartRate > 120 || log.heartRate < 50) {
            hrStyle = "font-weight:700; color:var(--danger); background-color:var(--danger-light); padding:4px 8px; border-radius:4px;";
        } else if (log.heartRate > 100 || log.heartRate < 60) {
            hrStyle = "font-weight:600; color:var(--warning);";
        }

        let stressStyle = "color:var(--success); font-weight:600;";
        if (log.stressLevel >= 8) {
            stressStyle = "color:var(--danger); font-weight:700;";
        } else if (log.stressLevel >= 5) {
            stressStyle = "color:var(--warning); font-weight:600;";
        }

        let tempStyle = "font-weight:500;";
        if (log.bodyTemperature >= 38.0 || log.bodyTemperature <= 35.0) {
            tempStyle = "font-weight:700; color:var(--danger);";
        }

        tr.innerHTML = `
            <td style="font-weight:500; color:var(--text-primary);">${formattedDate}</td>
            <td><span style="${hrStyle}">${log.heartRate} BPM</span></td>
            <td style="font-weight:500;">${log.bloodPressure}</td>
            <td><span style="${o2Style}">${log.oxygenLevel}%</span></td>
            <td><span style="${tempStyle}">${log.bodyTemperature.toFixed(1)}°C</span></td>
            <td><span style="${stressStyle}">${log.stressLevel}/10</span></td>
            <td style="text-transform: capitalize; font-weight: 500;">${log.mood}</td>
            <td>
                <span class="badge" style="font-size:12px; font-weight:600; padding:4px 8px; border-radius:12px; 
                      background-color:${getFatigueBadgeBg(log.fatigueLevel)}; 
                      color:${getFatigueBadgeColor(log.fatigueLevel)};">
                    ${log.fatigueLevel}
                </span>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function getFatigueBadgeBg(level) {
    if (level === "SEVERE") return "var(--danger-light)";
    if (level === "MODERATE") return "var(--warning-light)";
    if (level === "MILD") return "var(--primary-light)";
    return "var(--success-light)";
}

function getFatigueBadgeColor(level) {
    if (level === "SEVERE") return "var(--danger)";
    if (level === "MODERATE") return "var(--warning)";
    if (level === "MILD") return "var(--primary)";
    return "var(--success)";
}

function renderDetailedChart(logs) {
    const ctx = document.getElementById("detailedVitalsChart").getContext("2d");
    
    // Sort chronological (oldest to newest) and limit to last 10 logs
    const sortedLogs = logs.slice(0, 10).reverse();

    const labels = sortedLogs.map(l => new Date(l.createdAt).toLocaleDateString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }));
    const hrData = sortedLogs.map(l => l.heartRate);
    const o2Data = sortedLogs.map(l => l.oxygenLevel);
    const tempData = sortedLogs.map(l => l.bodyTemperature);
    const stressData = sortedLogs.map(l => l.stressLevel);

    if (detailedChartInstance) {
        detailedChartInstance.destroy();
    }

    detailedChartInstance = new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                {
                    label: 'Heart Rate (BPM)',
                    data: hrData,
                    borderColor: '#0ea5e9',
                    backgroundColor: 'rgba(14, 165, 233, 0.05)',
                    borderWidth: 3,
                    tension: 0.3,
                    yAxisID: 'y'
                },
                {
                    label: 'Oxygen Saturation (%)',
                    data: o2Data,
                    borderColor: '#10b981',
                    backgroundColor: 'rgba(16, 185, 129, 0.05)',
                    borderWidth: 3,
                    tension: 0.3,
                    yAxisID: 'y1'
                },
                {
                    label: 'Body Temp (°C)',
                    data: tempData,
                    borderColor: '#fbbf24',
                    backgroundColor: 'rgba(251, 191, 36, 0.05)',
                    borderWidth: 2,
                    borderDash: [5, 5],
                    tension: 0.3,
                    yAxisID: 'y2'
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: 'index',
                intersect: false,
            },
            scales: {
                y: {
                    type: 'linear',
                    display: true,
                    position: 'left',
                    title: { display: true, text: 'Heart Rate (BPM)' },
                    min: 40,
                    max: 160,
                    grid: { display: false }
                },
                y1: {
                    type: 'linear',
                    display: true,
                    position: 'right',
                    title: { display: true, text: 'Oxygen Saturation (%)' },
                    min: 75,
                    max: 100,
                    grid: { display: false }
                },
                y2: {
                    type: 'linear',
                    display: false, // Hidden but available
                    position: 'right',
                    min: 34,
                    max: 42
                }
            }
        }
    });
}

async function saveHealthVitals(event) {
    event.preventDefault();

    const payload = {
        heartRate: parseInt(document.getElementById("heartRate").value),
        bloodPressure: document.getElementById("bloodPressure").value,
        oxygenLevel: parseInt(document.getElementById("oxygenLevel").value),
        bodyTemperature: parseFloat(document.getElementById("bodyTemperature").value),
        stressLevel: parseInt(document.getElementById("stressLevel").value),
        mood: document.getElementById("mood").value,
        fatigueLevel: document.getElementById("fatigueLevel").value
    };

    try {
        const resp = await fetchWithAuth("/api/health/log", {
            method: "POST",
            body: JSON.stringify(payload)
        });

        if (resp && resp.ok) {
            showToast("Daily health metrics logged successfully!", "success");
            document.getElementById("health-log-form").reset();
            document.getElementById("stress-val").innerText = "5";
            
            // Reload historical data
            await fetchHealthHistory();
            
            // Trigger background AI Risk update scan
            fetchWithAuth("/api/ai/analyze", { method: "POST" });
        } else {
            showToast("Failed to log health vitals. Please check values.", "error");
        }
    } catch (err) {
        showToast("Error connecting to server. Please try again.", "error");
    }
}
