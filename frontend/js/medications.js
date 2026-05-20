// Nexura Medications Planner Engine

document.addEventListener("DOMContentLoaded", () => {
    // 1. Render Navigation Template
    renderSidebar("medications");

    // 2. Load Patient Data
    loadMedicationsPortal();
});

async function loadMedicationsPortal() {
    try {
        const userResp = await fetchWithAuth("/api/auth/me");
        if (!userResp) return;
        const user = await userResp.json();

        document.getElementById("profile-name").innerText = user.fullName;
        const avatar = user.fullName.split(" ").map(n => n[0]).join("").substring(0, 2).toUpperCase();
        document.getElementById("user-avatar").innerText = avatar;

        await fetchMedicationsPlan();
        await fetchComplianceStats();
    } catch (err) {
        // Handled
    }
}

async function fetchMedicationsPlan() {
    try {
        const resp = await fetchWithAuth("/api/medications");
        if (!resp) return;
        const data = await resp.json();

        renderMedicationCards(data);
    } catch (err) {
        showToast("Error retrieving medications plan.", "error");
    }
}

async function fetchComplianceStats() {
    try {
        const resp = await fetchWithAuth("/api/medications/compliance");
        if (!resp) return;
        const data = await resp.json();
        
        const rate = Math.round(data.complianceRate);
        document.getElementById("medications-compliance-val").innerText = `${rate}%`;
    } catch (err) {
        // Handled
    }
}

function renderMedicationCards(meds) {
    const grid = document.getElementById("medications-grid");
    grid.innerHTML = "";

    if (meds.length === 0) {
        grid.innerHTML = `
            <div style="grid-column: 1 / -1; text-align: center; color: var(--text-muted); padding: 60px 0;">
                <div style="font-size:40px; margin-bottom:12px;">💊</div>
                <h3>No medications scheduled yet</h3>
                <p style="font-size:14px; margin-top:8px;">Use the scheduler on the left to register your daily medicine routines.</p>
            </div>
        `;
        return;
    }

    meds.forEach(med => {
        const card = document.createElement("div");
        card.className = "card";
        card.style = "display: flex; flex-direction: column; justify-content: space-between; border-left: 5px solid var(--primary); min-height: 180px; padding: 20px;";

        if (!med.active) {
            card.style.borderLeft = "5px solid var(--text-muted)";
            card.style.opacity = 0.6;
        }

        const formattedTime = med.scheduledTime; // "08:00"

        card.innerHTML = `
            <div>
                <div style="display:flex; justify-content:space-between; align-items:start; margin-bottom:8px;">
                    <h3 style="font-size: 16px; font-weight:700; color:var(--text-primary);">${med.name}</h3>
                    <span style="font-size: 11px; font-weight:700; padding:4px 8px; border-radius:12px; 
                          background-color:var(--primary-light); color:var(--primary);">
                        ${med.frequency}
                    </span>
                </div>
                <div style="font-size: 13px; font-weight:600; color: var(--text-secondary); margin-bottom:6px;">Dosage: ${med.dosage}</div>
                <div style="font-size: 12px; color: var(--text-muted); line-height: 1.4; margin-bottom: 12px;">Notes: ${med.notes || "None"}</div>
                <div style="font-size:13px; font-weight:700; color:var(--primary); margin-bottom: 16px;">⏱️ Daily Intake: ${formattedTime}</div>
            </div>
            
            <div style="display:flex; gap:6px; border-top:1px solid var(--border-color); padding-top:12px;">
                <button class="btn btn-secondary" onclick="logMedTaken(${med.id})" style="flex-grow:1; font-size:12px; padding:8px; font-weight:700;">Taken</button>
                <button class="btn btn-secondary" onclick="editMedication(${JSON.stringify(med).replace(/"/g, '&quot;')})" style="font-size:12px; padding:8px 10px;">✏️</button>
                <button class="btn btn-secondary" onclick="deleteMedication(${med.id})" style="font-size:12px; padding:8px 10px; color:var(--danger);">✕</button>
            </div>
        `;
        grid.appendChild(card);
    });
}

async function saveMedication(event) {
    event.preventDefault();

    const id = document.getElementById("medication-id").value;
    const name = document.getElementById("medName").value.trim();
    const dosage = document.getElementById("medDosage").value.trim();
    const scheduledTime = document.getElementById("medTime").value; // "HH:MM"
    const frequency = document.getElementById("medFrequency").value;
    const notes = document.getElementById("medNotes").value.trim();

    const payload = {
        name,
        dosage,
        scheduledTime,
        frequency,
        notes,
        active: true
    };

    try {
        let resp;
        if (id) {
            // Update
            resp = await fetchWithAuth(`/api/medications/${id}`, {
                method: "PUT",
                body: JSON.stringify(payload)
            });
        } else {
            // Create
            resp = await fetchWithAuth("/api/medications", {
                method: "POST",
                body: JSON.stringify(payload)
            });
        }

        if (resp && resp.ok) {
            showToast(id ? "Medication updated successfully!" : "New medication schedule added!", "success");
            resetMedicationForm();
            await fetchMedicationsPlan();
            await fetchComplianceStats();
            
            // Re-adapt reminders timing automatically in background
            fetchWithAuth("/api/reminders/adapt", { method: "POST" });
        } else {
            showToast("Failed to schedule medication. Please check formats.", "error");
        }
    } catch (err) {
        showToast("Server connection error during schedule save.", "error");
    }
}

function editMedication(med) {
    document.getElementById("medication-id").value = med.id;
    document.getElementById("medName").value = med.name;
    document.getElementById("medDosage").value = med.dosage;
    document.getElementById("medTime").value = med.scheduledTime;
    document.getElementById("medFrequency").value = med.frequency;
    document.getElementById("medNotes").value = med.notes || "";

    document.getElementById("form-title").innerText = "Edit Medication";
    document.getElementById("save-btn").innerText = "Save Changes";
    document.getElementById("cancel-edit-btn").style.display = "inline-flex";
}

function resetMedicationForm() {
    document.getElementById("medication-id").value = "";
    document.getElementById("medication-form").reset();
    document.getElementById("form-title").innerText = "Schedule Medication";
    document.getElementById("save-btn").innerText = "Add Schedule";
    document.getElementById("cancel-edit-btn").style.display = "none";
}

async function deleteMedication(id) {
    const confirmDelete = confirm("Are you sure you wish to delete this scheduled medication? This will remove all adherence histories.");
    if (!confirmDelete) return;

    try {
        const resp = await fetchWithAuth(`/api/medications/${id}`, {
            method: "DELETE"
        });

        if (resp && resp.ok) {
            showToast("Medication deleted from plan.", "success");
            await fetchMedicationsPlan();
            await fetchComplianceStats();
        } else {
            showToast("Failed to delete medication.", "error");
        }
    } catch (err) {
        showToast("Error connecting to server.", "error");
    }
}

async function logMedTaken(id) {
    try {
        const resp = await fetchWithAuth(`/api/medications/${id}/taken`, {
            method: "POST"
        });

        if (resp && resp.ok) {
            showToast("Medication intake logged successfully!", "success");
            await fetchComplianceStats();
            // Trigger background AI Risk update scan
            fetchWithAuth("/api/ai/analyze", { method: "POST" });
        } else {
            showToast("Failed to record intake. Check schedules.", "error");
        }
    } catch (err) {
        showToast("Error connecting to server.", "error");
    }
}

async function adaptAllReminders() {
    try {
        showToast("AI analyzing rest patterns & delays...", "info");
        const resp = await fetchWithAuth("/api/reminders/adapt", { method: "POST" });
        if (resp && resp.ok) {
            const data = await resp.json();
            if (data.length > 0) {
                showToast(`AI adapted ${data.length} medication reminders to protect your rest!`, "success");
            } else {
                showToast("Medication reminder timings are already fully optimal.", "success");
            }
        }
    } catch (err) {
        showToast("Error starting AI timing adaptation.", "error");
    }
}
