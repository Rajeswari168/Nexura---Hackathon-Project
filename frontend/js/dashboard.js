// Nexura Dashboard Portal Engine
let vitalsChartInstance = null;

document.addEventListener("DOMContentLoaded", () => {
    // 1. Render Sidebar nav template
    renderSidebar("dashboard");

    // 2. Load Patient Data
    loadDashboardData();
});

async function loadDashboardData() {
    try {
        // Fetch User Info
        const userResp = await fetchWithAuth("/api/auth/me");
        if (!userResp) return;
        const user = await userResp.json();

        // Update profile displays
        document.getElementById("welcome-msg").innerText = `Good evening, ${user.fullName.split(' ')[0]}`;
        document.getElementById("profile-name").innerText = user.fullName;
        document.getElementById("caregiver-name").innerText = user.emergencyCaregiverName;
        document.getElementById("caregiver-phone").innerText = `Caregiver: ${user.caregiverPhoneNumber}`;
        
        const avatar = user.fullName.split(" ").map(n => n[0]).join("").substring(0, 2).toUpperCase();
        document.getElementById("user-avatar").innerText = avatar;

        // Fetch metrics concurrently
        fetchRiskStatus();
        fetchMedicationCompliance();
        fetchSleepMetrics();
        fetchTodayReminders();
        fetchVitalsChartData();
        fetchActiveAlerts();
        loadQuickChatHistory();
        
    } catch (err) {
        // Handled globally
    }
}

// 1. Fetch AI Overall Risk Level
async function fetchRiskStatus() {
    try {
        const resp = await fetchWithAuth("/api/ai/risk-status");
        if (!resp) return;
        const data = await resp.json();

        const container = document.getElementById("risk-container");
        const badge = document.getElementById("risk-level-badge");
        const findings = document.getElementById("risk-findings");

        // Dynamic theme class swap
        container.className = `risk-banner risk-${data.riskLevel}`;
        badge.innerText = data.riskLevel;
        findings.innerText = `${data.keyFindings} Rec: ${data.recommendations}`;
        
        // Update daily health score baseline from risk levels
        let score = 95;
        let caption = "Healthy baseline. Keep up your routine!";
        let strokeColor = "var(--success)";

        if (data.riskLevel === "MEDIUM") {
            score = 75;
            caption = "Mild stress or sleep inconsistency flagged.";
            strokeColor = "var(--warning)";
        } else if (data.riskLevel === "HIGH") {
            score = 55;
            caption = "High physiological warnings detected.";
            strokeColor = "var(--danger)";
        } else if (data.riskLevel === "CRITICAL") {
            score = 35;
            caption = "Critical threshold alert. Contact caregiver.";
            strokeColor = "var(--critical-light)";
        }

        updateHealthScoreCircle(score, caption, strokeColor);

    } catch (err) {
        // Fail gracefully
    }
}

function updateHealthScoreCircle(score, caption, color) {
    document.getElementById("health-score-val").innerText = `${score}%`;
    document.getElementById("score-caption").innerText = caption;

    const circle = document.getElementById("score-circle");
    if (circle) {
        circle.setAttribute("stroke", color);
        // Formula: Dash offset = circumference (314) - (score / 100 * 314)
        const offset = 314 - (score / 100 * 314);
        circle.style.strokeDashoffset = offset;
    }
}

// 2. Fetch Ingestion Compliance
async function fetchMedicationCompliance() {
    try {
        const resp = await fetchWithAuth("/api/medications/compliance");
        if (!resp) return;
        const data = await resp.json();
        const compliance = Math.round(data.complianceRate);
        document.getElementById("med-compliance-val").innerText = `${compliance}%`;
    } catch (err) {
        // Graceful
    }
}

// 3. Fetch Sleep Durations
async function fetchSleepMetrics() {
    try {
        const resp = await fetchWithAuth("/api/sleep/history");
        if (!resp) return;
        const data = await resp.json();

        if (data.length > 0) {
            const lastLog = data[0];
            document.getElementById("sleep-duration-val").innerText = `${lastLog.durationHours.toFixed(1)} hrs`;
            
            const qualityLbl = document.getElementById("sleep-quality-lbl");
            let qualStr = "Good";
            qualityLbl.style.color = "var(--success)";
            if (lastLog.sleepQuality <= 2) {
                qualStr = "Poor";
                qualityLbl.style.color = "var(--danger)";
            } else if (lastLog.sleepQuality === 3) {
                qualStr = "Fair";
                qualityLbl.style.color = "var(--warning)";
            }
            qualityLbl.innerText = `Quality: ${qualStr} (Score ${lastLog.sleepQuality}/5)`;
        } else {
            document.getElementById("sleep-duration-val").innerText = "-- hrs";
            document.getElementById("sleep-quality-lbl").innerText = "No logs recorded";
        }
    } catch (err) {
        // Graceful
    }
}

// 4. Fetch Pending Reminders
async function fetchTodayReminders() {
    try {
        const resp = await fetchWithAuth("/api/reminders");
        if (!resp) return;
        const data = await resp.json();

        const container = document.getElementById("reminders-list");
        container.innerHTML = "";

        if (data.length === 0) {
            container.innerHTML = `<div style="text-align: center; color: var(--text-muted); font-size: 13px; margin: auto;">All caught up! No pending medications.</div>`;
            return;
        }

        data.forEach(rem => {
            const med = rem.medication;
            const schedTime = new Date(rem.scheduledTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
            
            const div = document.createElement("div");
            div.style = "border: 1px solid var(--border-color); padding: 12px; border-radius: var(--border-radius-sm); display: flex; justify-content: space-between; align-items: center; background: var(--bg-primary); transition: var(--transition);";
            
            let notesText = med.dosage;
            if (rem.notesOverride) {
                notesText += ` • ${rem.notesOverride}`;
            }

            div.innerHTML = `
                <div>
                    <div style="font-weight:600; font-size:14px; color:var(--text-primary);">${med.name}</div>
                    <div style="font-size:12px; color:var(--text-secondary); margin-top:2px;">${notesText}</div>
                    <div style="font-size:12px; color:var(--primary); font-weight:600; margin-top:4px;">⏱️ ${schedTime}</div>
                </div>
                <div style="display:flex; gap:6px;">
                    <button class="btn btn-secondary" onclick="markReminderTaken(${med.id}, ${rem.id})" style="padding:6px 12px; font-size:12px; font-weight:700;">Take</button>
                    <button class="btn btn-secondary" onclick="snoozeReminder(${rem.id})" style="padding:6px 8px; font-size:12px;">Snooze</button>
                </div>
            `;
            container.appendChild(div);
        });
    } catch (err) {
        // Handled
    }
}

// 5. Ingestion & Snooze Controllers
async function markReminderTaken(medId, reminderId) {
    try {
        const resp = await fetchWithAuth(`/api/medications/${medId}/taken`, { method: "POST" });
        if (resp && resp.ok) {
            showToast("Medication ingestion logged!", "success");
            // Mark reminder as taken locally
            loadDashboardData();
        }
    } catch (err) {
        // Handled
    }
}

async function snoozeReminder(reminderId) {
    try {
        const resp = await fetchWithAuth(`/api/reminders/${reminderId}/snooze`, { method: "POST" });
        if (resp && resp.ok) {
            showToast("Reminder snoozed successfully.", "success");
            loadDashboardData();
        }
    } catch (err) {
        // Handled
    }
}

async function adaptReminders() {
    try {
        showToast("AI analyzing compliance & sleep patterns...", "info");
        const resp = await fetchWithAuth("/api/reminders/adapt", { method: "POST" });
        if (resp && resp.ok) {
            const data = await resp.json();
            if (data.length > 0) {
                showToast(`AI dynamically adjusted ${data.length} reminders!`, "success");
            } else {
                showToast("Medication timing is currently fully aligned.", "success");
            }
            loadDashboardData();
        }
    } catch (err) {
        // Graceful
    }
}

// 6. Draw Chart.js Vitals Graphic
async function fetchVitalsChartData() {
    try {
        const resp = await fetchWithAuth("/api/health/history");
        if (!resp) return;
        const data = await resp.json();

        // Slice last 7 readings (Chronological)
        const recentReadings = data.slice(0, 7).reverse();

        const labels = recentReadings.map(r => new Date(r.createdAt).toLocaleDateString([], { month: 'short', day: 'numeric' }));
        const oxygenData = recentReadings.map(r => r.oxygenLevel || 95);
        const hrData = recentReadings.map(r => r.heartRate || 75);

        const ctx = document.getElementById('vitalsChart').getContext('2d');
        if (vitalsChartInstance) {
            vitalsChartInstance.destroy();
        }

        vitalsChartInstance = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [
                    {
                        label: 'O2 Saturation (%)',
                        data: oxygenData,
                        borderColor: '#10b981',
                        backgroundColor: 'rgba(16, 185, 129, 0.1)',
                        borderWidth: 2,
                        tension: 0.3,
                        yAxisID: 'y'
                    },
                    {
                        label: 'Heart Rate (BPM)',
                        data: hrData,
                        borderColor: '#0ea5e9',
                        backgroundColor: 'rgba(14, 165, 233, 0.1)',
                        borderWidth: 2,
                        tension: 0.3,
                        yAxisID: 'y1'
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    y: {
                        type: 'linear',
                        display: true,
                        position: 'left',
                        min: 80,
                        max: 100,
                        grid: { display: false }
                    },
                    y1: {
                        type: 'linear',
                        display: true,
                        position: 'right',
                        min: 40,
                        max: 140,
                        grid: { display: false }
                    }
                }
            }
        });
    } catch (err) {
        // Graceful
    }
}

// 7. Active Notification Alert Streams
async function fetchActiveAlerts() {
    try {
        const resp = await fetchWithAuth("/api/alerts");
        if (!resp) return;
        const data = await resp.json();

        const container = document.getElementById("dashboard-alerts");
        container.innerHTML = "";

        const unread = data.filter(a => !a.isRead);
        if (unread.length === 0) {
            container.innerHTML = `<div style="color: var(--text-muted); text-align: center; font-size: 13px; margin: 20px auto;">No active alerts logged.</div>`;
            return;
        }

        unread.forEach(alert => {
            const div = document.createElement("div");
            div.style = "border: 1px solid var(--border-color); padding: 12px 16px; border-radius: var(--border-radius-sm); position: relative; background: var(--bg-secondary); border-left: 5px solid var(--danger); transition: var(--transition);";
            
            if (alert.priority === "CRITICAL") {
                div.style.borderLeft = "5px solid var(--critical-light)";
                div.style.backgroundColor = "#fff5f5";
            } else if (alert.category === "SLEEP_DETERIORATION") {
                div.style.borderLeft = "5px solid #818cf8";
            }

            div.innerHTML = `
                <div style="font-weight: 700; font-size: 14px; display:flex; justify-content:space-between; align-items:center;">
                    <span>${alert.title}</span>
                    <button onclick="dismissAlert(${alert.id}, this)" style="background:none; border:none; color:var(--text-muted); cursor:pointer; font-weight:700; font-size:14px;">✕</button>
                </div>
                <p style="font-size: 13px; color: var(--text-secondary); margin-top:4px; line-height: 1.4;">${alert.message}</p>
            `;
            container.appendChild(div);
        });
    } catch (err) {
        // Handled
    }
}

async function dismissAlert(id, btn) {
    try {
        const resp = await fetchWithAuth(`/api/alerts/${id}/read`, { method: "PUT" });
        if (resp && resp.ok) {
            showToast("Alert cleared.", "success");
            // Slide dismiss element locally
            const card = btn.closest("div");
            card.style.transform = "translateX(100%)";
            card.style.opacity = 0;
            setTimeout(() => {
                loadDashboardData();
            }, 300);
        }
    } catch (err) {
        // Handled
    }
}

// 8. Dynamic AI Scan
async function triggerAIDeteriorationScan() {
    try {
        showToast("Triggering full clinical scan...", "info");
        const resp = await fetchWithAuth("/api/ai/analyze", { method: "POST" });
        if (resp && resp.ok) {
            showToast("Clinical analysis scan completed!", "success");
            loadDashboardData();
        }
    } catch (err) {
        // Handled
    }
}

// 9. Manual Escalation
async function triggerEmergencyEscalation() {
    const confirmation = confirm("This will simulate immediately notifying your Caregiver via SMS & Email. Are you sure you wish to trigger this?");
    if (!confirmation) return;

    try {
        showToast("Initiating Caregiver Escalation cycle...", "warning");
        const resp = await fetchWithAuth("/api/emergency/escalate", {
            method: "POST",
            body: JSON.stringify({ reason: "Manual patient-initiated critical warning trigger from portal dashboard." })
        });
        if (resp && resp.ok) {
            showToast("Caregiver notified successfully! Alerts dispatches simulating logged in backend.", "success");
            loadDashboardData();
        }
    } catch (err) {
        // Handled
    }
}

// 10. Empathy Quick Chat Panel Integration
async function loadQuickChatHistory() {
    try {
        const resp = await fetchWithAuth("/api/chat/history");
        if (!resp) return;
        const data = await resp.json();

        const history = document.getElementById("chat-quick-history");
        history.innerHTML = "";

        if (data.length === 0) {
            history.innerHTML = `<div class="chat-bubble ai" style="padding:8px 12px; font-size: 13px;">Hi! I'm Nexura, your chronic companion. How are you feeling today?</div>`;
            return;
        }

        data.slice(-3).forEach(turn => {
            // User Message
            const userDiv = document.createElement("div");
            userDiv.className = "chat-bubble user";
            userDiv.style = "padding:8px 12px; font-size: 13px; margin: 4px 0;";
            userDiv.innerText = turn.message;
            history.appendChild(userDiv);

            // AI Response
            const aiDiv = document.createElement("div");
            aiDiv.className = "chat-bubble ai";
            aiDiv.style = "padding:8px 12px; font-size: 13px; margin: 4px 0;";
            aiDiv.innerText = turn.aiResponse;
            history.appendChild(aiDiv);
        });

        history.scrollTop = history.scrollHeight;
    } catch (err) {
        console.error("Quick chat history error:", err);
    }
}

async function sendQuickChatMessage() {
    const input = document.getElementById("chat-quick-input");
    const msg = input.value.trim();
    if (!msg) return;

    input.value = "";

    // Append message locally
    const history = document.getElementById("chat-quick-history");
    const userDiv = document.createElement("div");
    userDiv.className = "chat-bubble user";
    userDiv.style = "padding:8px 12px; font-size: 13px; margin: 4px 0;";
    userDiv.innerText = msg;
    history.appendChild(userDiv);
    history.scrollTop = history.scrollHeight;

    // Append simulated Typing bubble
    const typing = document.createElement("div");
    typing.className = "typing-indicator";
    typing.style = "padding:8px 12px; margin: 4px 0;";
    typing.innerHTML = `<div class="typing-dot"></div><div class="typing-dot"></div><div class="typing-dot"></div>`;
    history.appendChild(typing);
    history.scrollTop = history.scrollHeight;

    try {
        const resp = await fetchWithAuth("/api/chat/message", {
            method: "POST",
            body: JSON.stringify({ message: msg })
        });
        
        typing.remove();

        if (resp && resp.ok) {
            loadQuickChatHistory();
        } else {
            showToast("Failed to fetch chatbot response.", "error");
        }
    } catch (err) {
        typing.remove();
        console.error("Quick chat error:", err);
    }
}
