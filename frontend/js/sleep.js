// Nexura Sleep Portal logic
let sleepChartInstance = null;

document.addEventListener("DOMContentLoaded", () => {
    // 1. Render Navigation Template
    renderSidebar("sleep");

    // 2. Load Patient Profile & Sleep metrics
    loadSleepPortal();
    
    // Default form time setup (set Sleep Start to yesterday 10pm and Wake to today 6am for ease of logging)
    const now = new Date();
    const yesterday = new Date();
    yesterday.setDate(now.getDate() - 1);
    yesterday.setHours(22, 0, 0, 0); // 10 PM
    now.setHours(6, 0, 0, 0); // 6 AM
    
    document.getElementById("sleepStartTime").value = yesterday.toISOString().slice(0, 16);
    document.getElementById("wakeTime").value = now.toISOString().slice(0, 16);
});

async function loadSleepPortal() {
    try {
        const userResp = await fetchWithAuth("/api/auth/me");
        if (!userResp) return;
        const user = await userResp.json();

        document.getElementById("profile-name").innerText = user.fullName;
        const avatar = user.fullName.split(" ").map(n => n[0]).join("").substring(0, 2).toUpperCase();
        document.getElementById("user-avatar").innerText = avatar;

        await fetchSleepHistory();
    } catch (err) {
        // Handled
    }
}

async function fetchSleepHistory() {
    try {
        const resp = await fetchWithAuth("/api/sleep/history");
        if (!resp) return;
        const data = await resp.json();

        renderSleepHistoryRows(data);
        renderSleepChart(data);
    } catch (err) {
        showToast("Error retrieving sleep history logs.", "error");
    }
}

function renderSleepHistoryRows(logs) {
    const tbody = document.getElementById("sleep-history-rows");
    tbody.innerHTML = "";

    if (logs.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="6" style="text-align: center; color: var(--text-muted); padding: 40px 0;">
                    No sleep cycles recorded. Record your first sleep window on the left.
                </td>
            </tr>
        `;
        return;
    }

    logs.forEach(log => {
        const tr = document.createElement("tr");

        const sleepStart = new Date(log.sleepStartTime).toLocaleString([], {
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });

        const wakeTime = new Date(log.wakeTime).toLocaleString([], {
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });

        const loggedAt = new Date(log.createdAt || new Date()).toLocaleDateString([], {
            month: 'short',
            day: 'numeric'
        });

        // Highlight durations less than 5 hours (deterioration risk)
        let durStyle = "font-weight:600;";
        if (log.durationHours < 5.0) {
            durStyle = "font-weight:700; color:var(--danger); background-color:var(--danger-light); padding:4px 8px; border-radius:4px;";
        } else if (log.durationHours >= 8.0) {
            durStyle = "font-weight:600; color:var(--success);";
        }

        // Quality rating stars
        let qualityText = "";
        if (log.sleepQuality <= 2) {
            qualityText = `🔴 Poor (${log.sleepQuality}/5)`;
        } else if (log.sleepQuality === 3) {
            qualityText = `🟡 Fair (${log.sleepQuality}/5)`;
        } else {
            qualityText = `🟢 Excellent (${log.sleepQuality}/5)`;
        }

        tr.innerHTML = `
            <td style="font-weight:500;">${sleepStart}</td>
            <td style="font-weight:500;">${wakeTime}</td>
            <td><span style="${durStyle}">${log.durationHours.toFixed(1)} hours</span></td>
            <td style="font-weight:600;">${qualityText}</td>
            <td style="font-weight:500;">${log.interruptionsCount}</td>
            <td style="color: var(--text-secondary); font-size:13px;">${loggedAt}</td>
        `;
        tbody.appendChild(tr);
    });
}

function renderSleepChart(logs) {
    const ctx = document.getElementById("detailedSleepChart").getContext("2d");
    
    // Reverse logs to display chronological order (oldest to newest), limit to last 10 logs
    const recentLogs = logs.slice(0, 10).reverse();

    const labels = recentLogs.map(l => new Date(l.wakeTime).toLocaleDateString([], { month: 'short', day: 'numeric' }));
    const durationData = recentLogs.map(l => l.durationHours);
    const qualityData = recentLogs.map(l => l.sleepQuality);

    if (sleepChartInstance) {
        sleepChartInstance.destroy();
    }

    sleepChartInstance = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [
                {
                    type: 'bar',
                    label: 'Sleep Duration (Hours)',
                    data: durationData,
                    backgroundColor: 'rgba(129, 140, 248, 0.7)',
                    borderColor: '#818cf8',
                    borderWidth: 2,
                    borderRadius: 8,
                    yAxisID: 'y'
                },
                {
                    type: 'line',
                    label: 'Sleep Quality Score (1-5)',
                    data: qualityData,
                    borderColor: '#10b981',
                    backgroundColor: 'rgba(16, 185, 129, 0.1)',
                    borderWidth: 3,
                    tension: 0.3,
                    yAxisID: 'y1'
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
                    title: { display: true, text: 'Duration (Hours)' },
                    min: 0,
                    max: 16,
                    grid: { display: false }
                },
                y1: {
                    type: 'linear',
                    display: true,
                    position: 'right',
                    title: { display: true, text: 'Sleep Quality (1-5)' },
                    min: 0,
                    max: 6,
                    grid: { display: false }
                }
            }
        }
    });
}

async function saveSleepWindow(event) {
    event.preventDefault();

    const sleepStart = new Date(document.getElementById("sleepStartTime").value);
    const wakeTime = new Date(document.getElementById("wakeTime").value);

    if (wakeTime <= sleepStart) {
        showToast("Wake time must be after sleep start time.", "warning");
        return;
    }

    const payload = {
        sleepStartTime: sleepStart.toISOString(),
        wakeTime: wakeTime.toISOString(),
        sleepQuality: parseInt(document.getElementById("sleepQuality").value),
        interruptionsCount: parseInt(document.getElementById("interruptionsCount").value)
    };

    try {
        const resp = await fetchWithAuth("/api/sleep/log", {
            method: "POST",
            body: JSON.stringify(payload)
        });

        if (resp && resp.ok) {
            showToast("Sleep journal entry logged successfully!", "success");
            
            // Reload historical list
            await fetchSleepHistory();
            
            // Trigger background AI Risk update scan
            fetchWithAuth("/api/ai/analyze", { method: "POST" });
        } else {
            showToast("Failed to save sleep details. Check input limits.", "error");
        }
    } catch (err) {
        showToast("Server connection error during sleep save.", "error");
    }
}
