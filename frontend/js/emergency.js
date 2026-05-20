// Nexura Emergency & Caregiver Support Portal Engine

document.addEventListener("DOMContentLoaded", () => {
    // 1. Render Sidebar nav template
    renderSidebar("emergency");

    // 2. Load Patient & Caregiver Data
    loadEmergencyData();
});

async function loadEmergencyData() {
    try {
        // Fetch User Info
        const userResp = await fetchWithAuth("/api/auth/me");
        if (!userResp) return;
        const user = await userResp.json();

        // Update profile widget
        document.getElementById("profile-name").innerText = user.fullName;
        const avatar = user.fullName.split(" ").map(n => n[0]).join("").substring(0, 2).toUpperCase();
        document.getElementById("user-avatar").innerText = avatar;

        // Update Caregiver Info card
        document.getElementById("caregiver-full-name").innerText = user.emergencyCaregiverName;
        document.getElementById("caregiver-telephone").innerText = `📞 ${user.caregiverPhoneNumber}`;
        
        const cgInitials = user.emergencyCaregiverName 
            ? user.emergencyCaregiverName.split(" ").map(n => n[0]).join("").substring(0, 2).toUpperCase()
            : "CG";
        document.getElementById("caregiver-initials").innerText = cgInitials;

        // Fetch Emergency History Audit log
        fetchEmergencyHistory();

    } catch (err) {
        console.error("Error loading emergency contact dataset:", err);
    }
}

async function fetchEmergencyHistory() {
    const container = document.getElementById("audit-timeline-container");
    try {
        const resp = await fetchWithAuth("/api/emergency/history");
        if (!resp) {
            container.innerHTML = `<div style="text-align: center; color: var(--text-muted); font-size:14px; margin-top:40px;">Authentication error. Please log in.</div>`;
            return;
        }
        const data = await resp.json();

        container.innerHTML = "";

        if (data.length === 0) {
            container.innerHTML = `
                <div style="text-align: center; color: var(--text-muted); font-size: 14px; margin: 60px auto;">
                    <div style="font-size:40px; margin-bottom:12px;">🌱</div>
                    <h4 style="font-weight:600; color:var(--text-primary);">No Emergency Incidents Active</h4>
                    <p style="font-size:13px; margin-top:4px;">No critical warning dispatches have been registered for your profile.</p>
                </div>
            `;
            return;
        }

        data.forEach(event => {
            const item = document.createElement("div");
            item.className = "timeline-item";
            
            // Format dates
            const dateObj = new Date(event.escalatedAt);
            const formattedDate = dateObj.toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' }) + 
                                  " at " + 
                                  dateObj.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

            const statusClass = event.status === "RESOLVED" ? "resolved" : "";
            const statusText = event.status || "ESCALATED";

            item.innerHTML = `
                <div class="timeline-marker ${statusClass}"></div>
                <div class="timeline-header">
                    <span class="timeline-title" style="display:flex; align-items:center; gap:8px;">
                        ⚠️ Caregiver Notification
                        <span class="badge ${event.status === 'RESOLVED' ? 'badge-low' : 'badge-critical'}" style="font-size:10px;">${statusText}</span>
                    </span>
                    <span class="timeline-time">${formattedDate}</span>
                </div>
                <p class="timeline-body">${event.triggerReason}</p>
                <div style="display:flex; gap:8px; margin-top:8px;">
                    <div class="dispatch-badge">📱 SMS: DISPATCHED</div>
                    <div class="dispatch-badge">✉️ Email: SENT</div>
                </div>
            `;
            container.appendChild(item);
        });

    } catch (err) {
        container.innerHTML = `<div style="text-align: center; color: var(--text-danger); font-size:14px; margin-top:40px;">Failed to retrieve communications history.</div>`;
    }
}

async function triggerEscalationBtn() {
    const input = document.getElementById("escalation-reason");
    const reason = input.value.trim();

    if (!reason) {
        showToast("Please provide a reason for triggering escalation.", "warning");
        input.focus();
        return;
    }

    const confirmation = confirm("WARNING: This will dispatch immediate priority warning signals to your caregiver. Are you sure you wish to trigger this?");
    if (!confirmation) return;

    try {
        showToast("Initiating Caregiver Escalation cycle...", "warning");
        
        const resp = await fetchWithAuth("/api/emergency/escalate", {
            method: "POST",
            body: JSON.stringify({ reason: reason })
        });

        if (resp && resp.ok) {
            const data = await resp.json();
            showToast("Caregiver emergency notifications dispatched successfully!", "success");
            
            // Clear input
            input.value = "";
            
            // Reload historical audit trail logs
            loadEmergencyData();
        } else {
            showToast("Failed to trigger escalation. Verify server connection.", "error");
        }
    } catch (err) {
        showToast("Communication escalation failed.", "error");
    }
}
