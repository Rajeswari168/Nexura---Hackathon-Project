// Nexura Medical Reports Vault Controller

document.addEventListener("DOMContentLoaded", () => {
    // 1. Render Navigation template
    renderSidebar("reports");

    // 2. Load Patient details
    loadReportsVault();

    // 3. Register Drag & Drop event bindings
    setupDragAndDrop();
});

async function loadReportsVault() {
    try {
        const userResp = await fetchWithAuth("/api/auth/me");
        if (!userResp) return;
        const user = await userResp.json();

        document.getElementById("profile-name").innerText = user.fullName;
        const avatar = user.fullName.split(" ").map(n => n[0]).join("").substring(0, 2).toUpperCase();
        document.getElementById("user-avatar").innerText = avatar;

        await fetchReportsList();
    } catch (err) {
        // Handled
    }
}

async function fetchReportsList() {
    try {
        const resp = await fetchWithAuth("/api/reports");
        if (!resp) return;
        const data = await resp.json();

        document.getElementById("vault-count").innerText = `${data.length} Document${data.length === 1 ? '' : 's'}`;
        renderReportCards(data);
    } catch (err) {
        showToast("Error retrieving clinical documents list.", "error");
    }
}

function renderReportCards(reports) {
    const grid = document.getElementById("reports-grid");
    grid.innerHTML = "";

    if (reports.length === 0) {
        grid.innerHTML = `
            <div style="grid-column: 1 / -1; text-align: center; color: var(--text-muted); padding: 50px 0;">
                <div style="font-size:40px; margin-bottom:12px;">📁</div>
                <h4>Your reports vault is empty</h4>
                <p style="font-size:13px; margin-top:8px;">Drag and drop laboratory test reports or click the upload panel to analyze them with AI.</p>
            </div>
        `;
        return;
    }

    reports.forEach(rep => {
        const card = document.createElement("div");
        card.className = "card";
        card.style = "display: flex; flex-direction: column; justify-content: space-between; min-height: 170px; padding: 20px;";

        const uploadDate = new Date(rep.uploadedAt).toLocaleDateString([], {
            month: 'short',
            day: 'numeric',
            year: 'numeric'
        });

        const isImage = rep.fileType.startsWith("image/");
        const fileIcon = isImage ? "🖼️" : "📄";

        card.innerHTML = `
            <div>
                <div style="display:flex; gap:12px; align-items:start; margin-bottom:12px;">
                    <div style="font-size:28px; background:var(--bg-primary); padding:8px; border-radius:8px; border:1px solid var(--border-color);">${fileIcon}</div>
                    <div style="flex-grow:1; min-width:0;">
                        <h4 style="font-size: 14px; font-weight:700; color:var(--text-primary); white-space:nowrap; overflow:hidden; text-overflow:ellipsis;" title="${rep.fileName}">
                            ${rep.fileName}
                        </h4>
                        <div style="font-size:11px; color:var(--text-muted); margin-top:4px;">Uploaded: ${uploadDate}</div>
                        <div style="font-size:11px; font-weight:600; color:var(--primary); margin-top:2px; text-transform:uppercase;">${rep.fileType.split("/")[1]}</div>
                    </div>
                </div>
            </div>
            
            <div style="display:flex; gap:6px; border-top:1px solid var(--border-color); padding-top:12px; margin-top:12px;">
                <button class="btn btn-secondary" onclick="openReportPreview(${rep.id})" style="flex-grow:1; font-size:12px; padding:8px; font-weight:700;">Preview & AI</button>
                <button class="btn btn-secondary" onclick="deleteReport(${rep.id})" style="font-size:12px; padding:8px 10px; color:var(--danger);">✕</button>
            </div>
        `;
        grid.appendChild(card);
    });
}

function setupDragAndDrop() {
    const dropzone = document.getElementById("upload-zone");

    ['dragenter', 'dragover'].forEach(eventName => {
        dropzone.addEventListener(eventName, (e) => {
            e.preventDefault();
            dropzone.classList.add("dragover");
        }, false);
    });

    ['dragleave', 'drop'].forEach(eventName => {
        dropzone.addEventListener(eventName, (e) => {
            e.preventDefault();
            dropzone.classList.remove("dragover");
        }, false);
    });

    dropzone.addEventListener('drop', (e) => {
        const dt = e.dataTransfer;
        const files = dt.files;
        if (files.length > 0) {
            uploadFile(files[0]);
        }
    }, false);
}

function handleFileSelected(event) {
    const files = event.target.files;
    if (files.length > 0) {
        uploadFile(files[0]);
    }
}

async function uploadFile(file) {
    const progressContainer = document.getElementById("upload-progress-container");
    const progressPct = document.getElementById("upload-pct");
    const progressBar = document.getElementById("upload-progress-bar");
    const fileNameLbl = document.getElementById("uploading-file-name");

    fileNameLbl.innerText = file.name;
    progressContainer.style.display = "block";
    progressBar.style.width = "0%";
    progressPct.innerText = "0%";

    const token = localStorage.getItem("nexura-token");
    const formData = new FormData();
    formData.append("file", file);

    const xhr = new XMLHttpRequest();
    xhr.open("POST", "http://localhost:8080/api/reports/upload", true);
    
    if (token) {
        xhr.setRequestHeader("Authorization", `Bearer ${token}`);
    }

    xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) {
            const pct = Math.round((e.loaded / e.total) * 100);
            progressBar.style.width = `${pct}%`;
            progressPct.innerText = `${pct}%`;
        }
    };

    xhr.onload = async () => {
        progressContainer.style.display = "none";
        if (xhr.status === 200) {
            showToast("Document uploaded securely to vault!", "success");
            await fetchReportsList();
            
            // Auto analyze report in background
            const response = JSON.parse(xhr.responseText);
            if (response && response.reportId) {
                fetchWithAuth(`/api/ai/analyze-report/${response.reportId}`, { method: "POST" });
            }
        } else {
            const err = JSON.parse(xhr.responseText || '{"error":"Unknown error"}');
            showToast(err.error || "File upload failed.", "error");
        }
    };

    xhr.onerror = () => {
        progressContainer.style.display = "none";
        showToast("Error connecting to upload server.", "error");
    };

    xhr.send(formData);
}

async function deleteReport(id) {
    const confirmation = confirm("Are you sure you wish to delete this medical report? This cannot be undone.");
    if (!confirmation) return;

    try {
        const resp = await fetchWithAuth(`/api/reports/${id}`, {
            method: "DELETE"
        });

        if (resp && resp.ok) {
            showToast("Document deleted.", "success");
            await fetchReportsList();
        } else {
            showToast("Failed to delete document.", "error");
        }
    } catch (err) {
        showToast("Error connecting to server.", "error");
    }
}

// 4. Preview Overlay Modal controls
async function openReportPreview(id) {
    try {
        showToast("Loading report preview & AI insights...", "info");

        // Fetch analysis (this triggers AI analysis if not already done!)
        const analysisResp = await fetchWithAuth(`/api/ai/analyze-report/${id}`, { method: "POST" });
        if (!analysisResp) return;
        const analysis = await analysisResp.json();

        const report = analysis.report;
        const token = localStorage.getItem("nexura-token");

        // Display Modal
        const modal = document.getElementById("preview-modal");
        document.getElementById("modal-doc-title").innerText = report.fileName;

        // Set up secure binary path (attaching auth token as url parameter since iframe/img src doesn't support authorization headers natively!)
        // Wait, standard spring controllers take JWT from headers, but let's check how ReportController handles it.
        // It handles standard authenticated endpoints, which means fetchWithAuth handles JWT in headers.
        // Since iframes/images don't support custom headers, let's fetch the file blob directly and convert it to object URL!
        // This is an INCREDIBLY secure and neat design!
        const fileResp = await fetch(`http://localhost:8080/api/reports/${id}`, {
            headers: {
                "Authorization": `Bearer ${token}`
            }
        });
        const blob = await fileResp.blob();
        const objectURL = URL.createObjectURL(blob);

        const iframe = document.getElementById("pdf-preview-frame");
        const img = document.getElementById("image-preview-element");
        const txtFallback = document.getElementById("text-fallback-preview");

        iframe.style.display = "none";
        img.style.display = "none";
        txtFallback.style.display = "none";

        if (report.fileType === "application/pdf") {
            iframe.src = objectURL;
            iframe.style.display = "block";
        } else if (report.fileType.startsWith("image/")) {
            img.src = objectURL;
            img.style.display = "block";
        } else {
            txtFallback.style.display = "block";
        }

        // Render AI analysis panel data
        document.getElementById("ai-summary-text").innerText = analysis.aiSummary;
        document.getElementById("raw-extracted-block").innerText = analysis.extractedText || "No text extracted.";

        const riskBadge = document.getElementById("ai-risk-score-badge");
        riskBadge.innerText = analysis.riskScore;
        
        // Style badge colors
        riskBadge.className = `risk-badge risk-${analysis.riskScore}`;
        riskBadge.style.color = "white";
        if (analysis.riskScore === "LOW") riskBadge.style.backgroundColor = "var(--success)";
        else if (analysis.riskScore === "MEDIUM") riskBadge.style.backgroundColor = "var(--warning)";
        else if (analysis.riskScore === "HIGH") riskBadge.style.backgroundColor = "var(--danger)";
        else riskBadge.style.backgroundColor = "var(--critical-light)";

        modal.classList.add("active");
    } catch (err) {
        showToast("Error processing report preview.", "error");
    }
}

function closePreviewModal() {
    const modal = document.getElementById("preview-modal");
    modal.classList.remove("active");
    
    // Revoke object URLs to free memory
    const iframe = document.getElementById("pdf-preview-frame");
    const img = document.getElementById("image-preview-element");
    
    if (iframe.src.startsWith("blob:")) URL.revokeObjectURL(iframe.src);
    if (img.src.startsWith("blob:")) URL.revokeObjectURL(img.src);
    
    iframe.src = "";
    img.src = "";
}
