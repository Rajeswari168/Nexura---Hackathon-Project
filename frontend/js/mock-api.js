/**
 * Nexura Mock API Layer
 * Intercepts all fetchWithAuth calls to simulate a full backend using localStorage.
 * This allows the app to run completely without a Java/Spring Boot backend.
 */

// ─── LocalStorage DB Helpers ──────────────────────────────────────────────────

const DB = {
    get(key, fallback = null) {
        try {
            const val = localStorage.getItem(`nexura-db-${key}`);
            return val ? JSON.parse(val) : fallback;
        } catch { return fallback; }
    },
    set(key, value) {
        localStorage.setItem(`nexura-db-${key}`, JSON.stringify(value));
    },
    delete(key) {
        localStorage.removeItem(`nexura-db-${key}`);
    }
};

// ─── Seed Demo Data ───────────────────────────────────────────────────────────

function seedDemoData() {
    // Only seed once
    if (DB.get("seeded")) return;

    const now = Date.now();
    const day = 86400000;

    // Seed health vitals (7 days of readings)
    DB.set("health", [
        { id: 1, heartRate: 72, bloodPressure: "120/80", oxygenLevel: 98, bodyTemperature: 36.6, stressLevel: 4, mood: "Calm", fatigueLevel: "NONE", createdAt: new Date(now - 0 * day).toISOString() },
        { id: 2, heartRate: 78, bloodPressure: "122/82", oxygenLevel: 97, bodyTemperature: 36.8, stressLevel: 6, mood: "Anxious", fatigueLevel: "MILD", createdAt: new Date(now - 1 * day).toISOString() },
        { id: 3, heartRate: 68, bloodPressure: "118/76", oxygenLevel: 99, bodyTemperature: 36.5, stressLevel: 3, mood: "Happy", fatigueLevel: "NONE", createdAt: new Date(now - 2 * day).toISOString() },
        { id: 4, heartRate: 85, bloodPressure: "130/86", oxygenLevel: 96, bodyTemperature: 37.1, stressLevel: 7, mood: "Stressed", fatigueLevel: "MODERATE", createdAt: new Date(now - 3 * day).toISOString() },
        { id: 5, heartRate: 74, bloodPressure: "121/79", oxygenLevel: 98, bodyTemperature: 36.7, stressLevel: 5, mood: "Calm", fatigueLevel: "MILD", createdAt: new Date(now - 4 * day).toISOString() },
        { id: 6, heartRate: 70, bloodPressure: "119/78", oxygenLevel: 98, bodyTemperature: 36.6, stressLevel: 4, mood: "Energetic", fatigueLevel: "NONE", createdAt: new Date(now - 5 * day).toISOString() },
        { id: 7, heartRate: 76, bloodPressure: "124/82", oxygenLevel: 97, bodyTemperature: 36.9, stressLevel: 5, mood: "Tired", fatigueLevel: "MILD", createdAt: new Date(now - 6 * day).toISOString() },
    ]);

    // Seed sleep logs (7 nights)
    DB.set("sleep", [
        { id: 1, sleepStartTime: new Date(now - 0 * day - 8 * 3600000).toISOString(), wakeTime: new Date(now - 0 * day).toISOString(), durationHours: 8.0, sleepQuality: 4, interruptionsCount: 1, createdAt: new Date(now - 0 * day).toISOString() },
        { id: 2, sleepStartTime: new Date(now - 1 * day - 6 * 3600000).toISOString(), wakeTime: new Date(now - 1 * day).toISOString(), durationHours: 6.0, sleepQuality: 3, interruptionsCount: 2, createdAt: new Date(now - 1 * day).toISOString() },
        { id: 3, sleepStartTime: new Date(now - 2 * day - 9 * 3600000).toISOString(), wakeTime: new Date(now - 2 * day).toISOString(), durationHours: 9.0, sleepQuality: 5, interruptionsCount: 0, createdAt: new Date(now - 2 * day).toISOString() },
        { id: 4, sleepStartTime: new Date(now - 3 * day - 4.5 * 3600000).toISOString(), wakeTime: new Date(now - 3 * day).toISOString(), durationHours: 4.5, sleepQuality: 2, interruptionsCount: 4, createdAt: new Date(now - 3 * day).toISOString() },
        { id: 5, sleepStartTime: new Date(now - 4 * day - 7.5 * 3600000).toISOString(), wakeTime: new Date(now - 4 * day).toISOString(), durationHours: 7.5, sleepQuality: 4, interruptionsCount: 1, createdAt: new Date(now - 4 * day).toISOString() },
        { id: 6, sleepStartTime: new Date(now - 5 * day - 8.5 * 3600000).toISOString(), wakeTime: new Date(now - 5 * day).toISOString(), durationHours: 8.5, sleepQuality: 5, interruptionsCount: 0, createdAt: new Date(now - 5 * day).toISOString() },
        { id: 7, sleepStartTime: new Date(now - 6 * day - 5 * 3600000).toISOString(), wakeTime: new Date(now - 6 * day).toISOString(), durationHours: 5.0, sleepQuality: 2, interruptionsCount: 3, createdAt: new Date(now - 6 * day).toISOString() },
    ]);

    // Seed medications
    DB.set("medications", [
        { id: 1, name: "Metformin", dosage: "500mg", scheduledTime: "08:00", frequency: "DAILY", notes: "Take with breakfast", active: true },
        { id: 2, name: "Lisinopril", dosage: "10mg", scheduledTime: "09:00", frequency: "DAILY", notes: "Blood pressure medication", active: true },
        { id: 3, name: "Atorvastatin", dosage: "20mg", scheduledTime: "21:00", frequency: "DAILY", notes: "Take at night for best effect", active: true },
    ]);

    // Seed medication intake logs
    DB.set("medication-intakes", [
        { medId: 1, takenAt: new Date(now - 0 * day).toISOString() },
        { medId: 2, takenAt: new Date(now - 0 * day).toISOString() },
        { medId: 1, takenAt: new Date(now - 1 * day).toISOString() },
        { medId: 2, takenAt: new Date(now - 1 * day).toISOString() },
        { medId: 3, takenAt: new Date(now - 1 * day).toISOString() },
        { medId: 1, takenAt: new Date(now - 2 * day).toISOString() },
        { medId: 2, takenAt: new Date(now - 2 * day).toISOString() },
        { medId: 3, takenAt: new Date(now - 2 * day).toISOString() },
    ]);

    // Seed reminders
    DB.set("reminders", [
        { id: 1, medication: { id: 1, name: "Metformin", dosage: "500mg" }, scheduledTime: new Date(now + 3600000).toISOString(), notesOverride: "With breakfast", status: "PENDING" },
        { id: 2, medication: { id: 3, name: "Atorvastatin", dosage: "20mg" }, scheduledTime: new Date(now + 7 * 3600000).toISOString(), notesOverride: "Take at night", status: "PENDING" },
    ]);

    // Seed alerts
    DB.set("alerts", [
        { id: 1, title: "⚠️ Sleep Duration Below 5 Hours", message: "Your sleep log on Day 4 showed only 4.5 hours with 4 interruptions. This may increase medication absorption delays.", category: "SLEEP_DETERIORATION", priority: "MEDIUM", isRead: false, createdAt: new Date(now - 3 * day).toISOString() },
        { id: 2, title: "📊 Elevated Stress Level Detected", message: "Stress level of 7/10 was recorded yesterday. Consider a mindfulness session or contact your caregiver.", category: "VITAL_WARNING", priority: "LOW", isRead: false, createdAt: new Date(now - 1 * day).toISOString() },
    ]);

    // Seed chat history
    DB.set("chat", [
        { id: 1, message: "How am I doing overall with my sleep?", aiResponse: "Based on your recent logs, your sleep has been inconsistent. You had a great 9-hour night two days ago, but struggled with only 4.5 hours last Thursday. I'd recommend aiming for a consistent 7-8 hour window. Would you like some sleep hygiene tips?", language: "en", createdAt: new Date(now - 2 * day).toISOString() },
        { id: 2, message: "What about my medications?", aiResponse: "Your medication compliance has been strong at around 87%! You've been consistent with Metformin and Lisinopril. Just make sure to take your Atorvastatin at night for the best cholesterol-lowering effect. Keep it up! 💊", language: "en", createdAt: new Date(now - 1 * day).toISOString() },
    ]);

    // Seed risk status
    DB.set("risk", {
        riskLevel: "MEDIUM",
        keyFindings: "Sleep inconsistency detected over the past 7 days with 2 poor-quality nights.",
        recommendations: "Maintain consistent sleep schedule. Monitor stress levels."
    });

    // Seed medical reports
    DB.set("reports", [
        {
            id: 1,
            fileName: "CBC_BloodTest_May2025.pdf",
            fileType: "application/pdf",
            uploadedAt: new Date(now - 5 * day).toISOString(),
            aiAnalysis: "Complete Blood Count results are within normal ranges. Hemoglobin: 14.2 g/dL (Normal). WBC: 6.8 K/μL (Normal). Platelets: 245 K/μL (Normal). No concerning abnormalities detected.",
            summary: "Normal CBC Panel"
        },
        {
            id: 2,
            fileName: "Echocardiogram_Report_Apr2025.pdf",
            fileType: "application/pdf",
            uploadedAt: new Date(now - 20 * day).toISOString(),
            aiAnalysis: "Echocardiogram shows mild left ventricular hypertrophy consistent with hypertension history. Ejection fraction is preserved at 60%. Recommend continued antihypertensive therapy and follow-up in 6 months.",
            summary: "Mild LV Hypertrophy"
        }
    ]);

    DB.set("seeded", true);
}

// ─── Mock Response Builder ────────────────────────────────────────────────────

function mockResponse(data, status = 200) {
    return {
        ok: status >= 200 && status < 300,
        status,
        json: async () => data,
        text: async () => JSON.stringify(data)
    };
}

// ─── AI Empathy Response Engine ───────────────────────────────────────────────

const AI_RESPONSES = {
    default: [
        "I understand how you're feeling. Your health data shows some important trends we should discuss together.",
        "Based on your recent vitals and sleep patterns, I'd recommend focusing on consistent rest tonight.",
        "Your dedication to logging your health metrics is wonderful! This helps me give you better insights.",
        "I'm here for you. Remember that managing a chronic condition is a journey, not a sprint.",
        "Your blood pressure readings have been relatively stable. Keep up with your Lisinopril schedule.",
        "I notice your stress levels have been slightly elevated this week. Would you like a quick mindfulness exercise?",
        "You're doing great tracking everything! Your medication compliance is something to be proud of.",
        "Small consistent actions every day lead to significant improvements in chronic condition management.",
    ],
    sleep: [
        "Your sleep data shows variation. Consistent sleep windows of 7-8 hours are ideal for medication efficacy.",
        "Poor sleep quality can affect how well your body responds to medications. Let's focus on sleep hygiene.",
        "I see you've been tracking your sleep interruptions — this is excellent! Aim for fewer than 2 interruptions.",
    ],
    medication: [
        "Your medication compliance is key to managing your condition effectively. I'm proud of your consistency!",
        "Remember: Atorvastatin works best when taken at night. Your evening routine matters!",
        "Metformin should always be taken with food to minimize stomach discomfort.",
    ],
    stress: [
        "Elevated stress can raise blood pressure temporarily. Try the 4-7-8 breathing technique.",
        "Chronic stress affects both sleep quality and medication absorption. Let's work on this together.",
        "I recommend 10 minutes of mindfulness meditation daily. Would you like me to guide you?",
    ],
    vitals: [
        "Your oxygen levels are excellent! SpO2 above 95% is a great sign.",
        "Heart rate trends look stable. Keep monitoring and report any readings above 110 BPM.",
        "Your body temperature readings are within the normal range — no fever concerns!",
    ],
    emergency: [
        "I'm detecting some concerning patterns. Please contact your caregiver immediately if you feel unwell.",
        "If you're experiencing chest pain, shortness of breath, or severe dizziness — call emergency services now.",
        "Your safety is the priority. Please reach out to your registered caregiver if symptoms persist.",
    ]
};

function generateAIResponse(message) {
    const lower = message.toLowerCase();
    let pool = AI_RESPONSES.default;

    if (lower.includes("sleep") || lower.includes("tired") || lower.includes("rest") || lower.includes("fatigue")) {
        pool = AI_RESPONSES.sleep;
    } else if (lower.includes("medication") || lower.includes("medicine") || lower.includes("pill") || lower.includes("dose")) {
        pool = AI_RESPONSES.medication;
    } else if (lower.includes("stress") || lower.includes("anxious") || lower.includes("worry") || lower.includes("anxiety")) {
        pool = AI_RESPONSES.stress;
    } else if (lower.includes("heart") || lower.includes("oxygen") || lower.includes("blood") || lower.includes("vital") || lower.includes("pressure")) {
        pool = AI_RESPONSES.vitals;
    } else if (lower.includes("emergency") || lower.includes("pain") || lower.includes("severe") || lower.includes("critical")) {
        pool = AI_RESPONSES.emergency;
    }

    return pool[Math.floor(Math.random() * pool.length)];
}

function detectEmotionMode(message) {
    const lower = message.toLowerCase();
    if (lower.includes("emergency") || lower.includes("critical") || lower.includes("chest pain") || lower.includes("can't breathe")) return "CRITICAL";
    if (lower.includes("pain") || lower.includes("severe") || lower.includes("bad") || lower.includes("terrible")) return "URGENT";
    if (lower.includes("worried") || lower.includes("scared") || lower.includes("anxious") || lower.includes("nervous") || lower.includes("stress")) return "EMPATHETIC";
    return "NORMAL";
}

// ─── Mock API Router ──────────────────────────────────────────────────────────

async function mockFetchWithAuth(endpoint, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const body = options.body ? JSON.parse(options.body) : null;

    // Simulate network latency (100-300ms)
    await new Promise(r => setTimeout(r, 100 + Math.random() * 200));

    // ── AUTH ──────────────────────────────────────────────────────────────────

    if (endpoint === "/api/auth/login") {
        const users = DB.get("users", []);
        const found = users.find(u => u.email === body.email && u.password === body.password);
        if (!found) return mockResponse("Invalid email or password.", 401);
        return mockResponse({ token: "mock-jwt-token-" + found.id, email: found.email, fullName: found.fullName });
    }

    if (endpoint === "/api/auth/register") {
        const users = DB.get("users", []);
        if (users.find(u => u.email === body.email)) {
            return mockResponse("Email already registered.", 409);
        }
        const newUser = {
            id: Date.now(),
            fullName: body.fullName,
            email: body.email,
            password: body.password,
            dateOfBirth: body.dateOfBirth,
            primaryCondition: body.primaryCondition,
            emergencyCaregiverName: body.emergencyCaregiverName || "Jane Doe",
            caregiverPhoneNumber: body.caregiverPhoneNumber || "+1 (800) 555-0199",
            aiProvider: "mock",
            groqKey: "", geminiKey: "", openaiKey: ""
        };
        users.push(newUser);
        DB.set("users", users);
        return mockResponse({ token: "mock-jwt-token-" + newUser.id, email: newUser.email, fullName: newUser.fullName });
    }

    if (endpoint === "/api/auth/me") {
        const users = DB.get("users", []);
        const email = localStorage.getItem("nexura-email");
        const user = users.find(u => u.email === email);
        if (!user) return mockResponse(null, 401);
        const { password, ...safeUser } = user;
        return mockResponse(safeUser);
    }

    if (endpoint === "/api/auth/settings" && method === "POST") {
        const users = DB.get("users", []);
        const email = localStorage.getItem("nexura-email");
        const idx = users.findIndex(u => u.email === email);
        if (idx >= 0) {
            users[idx] = { ...users[idx], ...body };
            DB.set("users", users);
        }
        return mockResponse({ success: true });
    }

    // ── HEALTH LOGS ───────────────────────────────────────────────────────────

    if (endpoint === "/api/health/history") {
        return mockResponse(DB.get("health", []));
    }

    if (endpoint === "/api/health/log" && method === "POST") {
        const logs = DB.get("health", []);
        const newLog = { id: Date.now(), ...body, createdAt: new Date().toISOString() };
        logs.unshift(newLog);
        DB.set("health", logs);
        // Re-evaluate risk after new health log
        recalculateRisk();
        return mockResponse(newLog);
    }

    // ── SLEEP ─────────────────────────────────────────────────────────────────

    if (endpoint === "/api/sleep/history") {
        return mockResponse(DB.get("sleep", []));
    }

    if (endpoint === "/api/sleep/log" && method === "POST") {
        const logs = DB.get("sleep", []);
        const sleepStart = new Date(body.sleepStartTime);
        const wakeTime = new Date(body.wakeTime);
        const durationHours = (wakeTime - sleepStart) / 3600000;
        const newLog = {
            id: Date.now(),
            ...body,
            durationHours: parseFloat(durationHours.toFixed(2)),
            createdAt: new Date().toISOString()
        };
        logs.unshift(newLog);
        DB.set("sleep", logs);
        recalculateRisk();
        return mockResponse(newLog);
    }

    // ── MEDICATIONS ───────────────────────────────────────────────────────────

    if (endpoint === "/api/medications" && method === "GET") {
        return mockResponse(DB.get("medications", []));
    }

    if (endpoint === "/api/medications" && method === "POST") {
        const meds = DB.get("medications", []);
        const newMed = { id: Date.now(), ...body };
        meds.push(newMed);
        DB.set("medications", meds);
        return mockResponse(newMed);
    }

    if (endpoint.match(/\/api\/medications\/\d+$/) && method === "PUT") {
        const id = parseInt(endpoint.split("/").pop());
        const meds = DB.get("medications", []);
        const idx = meds.findIndex(m => m.id === id);
        if (idx >= 0) {
            meds[idx] = { ...meds[idx], ...body };
            DB.set("medications", meds);
        }
        return mockResponse(meds[idx] || {});
    }

    if (endpoint.match(/\/api\/medications\/\d+$/) && method === "DELETE") {
        const id = parseInt(endpoint.split("/").pop());
        const meds = DB.get("medications", []);
        DB.set("medications", meds.filter(m => m.id !== id));
        return mockResponse({ success: true });
    }

    if (endpoint.match(/\/api\/medications\/\d+\/taken/) && method === "POST") {
        const medId = parseInt(endpoint.split("/")[3]);
        const intakes = DB.get("medication-intakes", []);
        intakes.push({ medId, takenAt: new Date().toISOString() });
        DB.set("medication-intakes", intakes);
        return mockResponse({ success: true });
    }

    if (endpoint === "/api/medications/compliance") {
        const meds = DB.get("medications", []).filter(m => m.active);
        const intakes = DB.get("medication-intakes", []);
        const sevenDaysAgo = Date.now() - 7 * 86400000;
        const recentIntakes = intakes.filter(i => new Date(i.takenAt).getTime() > sevenDaysAgo);
        const expected = meds.length * 7;
        const actual = recentIntakes.length;
        const rate = expected > 0 ? Math.min(100, (actual / expected) * 100) : 100;
        return mockResponse({ complianceRate: rate, totalExpected: expected, totalTaken: actual });
    }

    // ── REMINDERS ─────────────────────────────────────────────────────────────

    if (endpoint === "/api/reminders" && method === "GET") {
        return mockResponse(DB.get("reminders", []));
    }

    if (endpoint.match(/\/api\/reminders\/\d+\/snooze/) && method === "POST") {
        const id = parseInt(endpoint.split("/")[3]);
        const reminders = DB.get("reminders", []);
        const idx = reminders.findIndex(r => r.id === id);
        if (idx >= 0) {
            reminders[idx].scheduledTime = new Date(Date.now() + 30 * 60000).toISOString();
        }
        DB.set("reminders", reminders);
        return mockResponse({ success: true });
    }

    if (endpoint === "/api/reminders/adapt" && method === "POST") {
        // AI adapts reminders based on sleep patterns
        const sleepLogs = DB.get("sleep", []);
        const reminders = DB.get("reminders", []);
        const adapted = [];
        if (sleepLogs.length > 0) {
            const latestWake = new Date(sleepLogs[0].wakeTime);
            reminders.forEach(rem => {
                const schedTime = new Date(rem.scheduledTime);
                if (latestWake > schedTime) {
                    // Delay reminder to 30 mins after wake
                    rem.scheduledTime = new Date(latestWake.getTime() + 30 * 60000).toISOString();
                    adapted.push(rem);
                }
            });
            DB.set("reminders", reminders);
        }
        return mockResponse(adapted);
    }

    // ── ALERTS ────────────────────────────────────────────────────────────────

    if (endpoint === "/api/alerts" && method === "GET") {
        return mockResponse(DB.get("alerts", []));
    }

    if (endpoint.match(/\/api\/alerts\/\d+\/read/) && method === "PUT") {
        const id = parseInt(endpoint.split("/")[3]);
        const alerts = DB.get("alerts", []);
        const idx = alerts.findIndex(a => a.id === id);
        if (idx >= 0) alerts[idx].isRead = true;
        DB.set("alerts", alerts);
        return mockResponse({ success: true });
    }

    // ── AI RISK ENGINE ────────────────────────────────────────────────────────

    if (endpoint === "/api/ai/risk-status") {
        return mockResponse(DB.get("risk", {
            riskLevel: "LOW",
            keyFindings: "All vitals are within normal ranges.",
            recommendations: "Maintain current medication schedule and sleep routine."
        }));
    }

    if (endpoint === "/api/ai/analyze" && method === "POST") {
        recalculateRisk();
        return mockResponse({ success: true });
    }

    // ── CHAT ──────────────────────────────────────────────────────────────────

    if (endpoint === "/api/chat/history" && method === "GET") {
        return mockResponse(DB.get("chat", []));
    }

    if (endpoint === "/api/chat/history" && method === "DELETE") {
        DB.set("chat", []);
        return mockResponse({ success: true });
    }

    if (endpoint === "/api/chat/message" && method === "POST") {
        const chat = DB.get("chat", []);
        const reply = generateAIResponse(body.message);
        const emotion = detectEmotionMode(body.message);
        const turn = {
            id: Date.now(),
            message: body.message,
            aiResponse: reply,
            emotionMode: emotion,
            language: body.language || "en",
            createdAt: new Date().toISOString()
        };
        chat.push(turn);
        DB.set("chat", chat);
        return mockResponse({ reply, emotionMode: emotion, language: body.language || "en" });
    }

    // ── REPORTS ───────────────────────────────────────────────────────────────

    if (endpoint === "/api/reports" && method === "GET") {
        return mockResponse(DB.get("reports", []));
    }

    if (endpoint === "/api/reports" && method === "POST") {
        const reports = DB.get("reports", []);
        const newReport = {
            id: Date.now(),
            fileName: body.fileName,
            fileType: body.fileType || "application/pdf",
            uploadedAt: new Date().toISOString(),
            summary: "Pending AI Analysis...",
            aiAnalysis: "Document has been received and is being analyzed by the AI engine. Results will appear shortly."
        };
        reports.unshift(newReport);
        DB.set("reports", reports);

        // Simulate async AI analysis (update after 2 seconds)
        setTimeout(() => {
            const rpts = DB.get("reports", []);
            const idx = rpts.findIndex(r => r.id === newReport.id);
            if (idx >= 0) {
                rpts[idx].summary = "AI Analysis Complete";
                rpts[idx].aiAnalysis = `Analysis of "${body.fileName}": Document successfully processed. Key health metrics extracted and compared against your clinical baseline. No critical abnormalities detected in this report. Results have been integrated into your companion context for more personalized support.`;
                DB.set("reports", rpts);
            }
        }, 2500);

        return mockResponse(newReport);
    }

    if (endpoint.match(/\/api\/reports\/\d+/) && method === "DELETE") {
        const id = parseInt(endpoint.split("/").pop());
        const reports = DB.get("reports", []);
        DB.set("reports", reports.filter(r => r.id !== id));
        return mockResponse({ success: true });
    }

    // ── EMERGENCY ─────────────────────────────────────────────────────────────

    if (endpoint === "/api/emergency/escalate" && method === "POST") {
        const alerts = DB.get("alerts", []);
        alerts.unshift({
            id: Date.now(),
            title: "🚨 Manual Emergency Escalation Triggered",
            message: body.reason || "Patient triggered manual emergency alert. Caregiver has been notified via SMS and Email.",
            category: "EMERGENCY",
            priority: "CRITICAL",
            isRead: false,
            createdAt: new Date().toISOString()
        });
        DB.set("alerts", alerts);
        return mockResponse({ success: true, message: "Caregiver notified." });
    }

    // ── FALLBACK ──────────────────────────────────────────────────────────────
    console.warn(`[MockAPI] Unhandled endpoint: ${method} ${endpoint}`);
    return mockResponse({ error: "Not found" }, 404);
}

// ─── AI Risk Recalculation ────────────────────────────────────────────────────

function recalculateRisk() {
    const healthLogs = DB.get("health", []);
    const sleepLogs = DB.get("sleep", []);

    let riskScore = 0;
    let findings = [];
    let recommendations = [];

    // Evaluate recent sleep
    const recentSleep = sleepLogs.slice(0, 3);
    const poorSleepCount = recentSleep.filter(s => s.durationHours < 6 || s.sleepQuality <= 2).length;
    if (poorSleepCount >= 2) {
        riskScore += 3;
        findings.push("Multiple poor-quality sleep cycles detected recently.");
        recommendations.push("Improve sleep hygiene; aim for 7-8 hours nightly.");
    } else if (poorSleepCount === 1) {
        riskScore += 1;
        findings.push("One below-average sleep cycle logged this week.");
    }

    // Evaluate recent vitals
    if (healthLogs.length > 0) {
        const latest = healthLogs[0];
        if (latest.heartRate > 110 || latest.heartRate < 50) { riskScore += 3; findings.push("Abnormal heart rate detected."); }
        if (latest.oxygenLevel < 92) { riskScore += 4; findings.push("Low oxygen saturation detected. Seek medical attention."); }
        if (latest.stressLevel >= 8) { riskScore += 2; findings.push("Very high stress level logged."); recommendations.push("Practice relaxation techniques; consider contacting caregiver."); }
        if (latest.bodyTemperature >= 38.5) { riskScore += 3; findings.push("Fever detected."); recommendations.push("Rest and monitor temperature; seek medical attention if above 39°C."); }
    }

    let riskLevel = "LOW";
    if (riskScore >= 6) riskLevel = "CRITICAL";
    else if (riskScore >= 4) riskLevel = "HIGH";
    else if (riskScore >= 2) riskLevel = "MEDIUM";

    const defaultFinding = "All logged vitals are within normal ranges.";
    const defaultRec = "Continue maintaining your current health routine.";

    DB.set("risk", {
        riskLevel,
        keyFindings: findings.length > 0 ? findings.join(" ") : defaultFinding,
        recommendations: recommendations.length > 0 ? recommendations.join(" ") : defaultRec
    });

    // Generate alert if risk increased
    if (riskScore >= 4) {
        const alerts = DB.get("alerts", []);
        const alreadyAlerted = alerts.some(a => !a.isRead && a.category === "AI_RISK_UPDATE");
        if (!alreadyAlerted) {
            alerts.unshift({
                id: Date.now(),
                title: `🤖 AI Risk Level Updated: ${riskLevel}`,
                message: findings.join(" ") + " " + recommendations.join(" "),
                category: "AI_RISK_UPDATE",
                priority: riskScore >= 6 ? "CRITICAL" : "HIGH",
                isRead: false,
                createdAt: new Date().toISOString()
            });
            DB.set("alerts", alerts);
        }
    }
}

// ─── Initialize Mock Environment ─────────────────────────────────────────────

(function initMock() {
    // Auto-seed demo data on first run
    seedDemoData();

    // Auto-login demo user if no session exists
    const token = localStorage.getItem("nexura-token");
    if (!token) {
        // Ensure demo user exists
        const users = DB.get("users", []);
        if (!users.find(u => u.email === "demo@nexura.health")) {
            users.push({
                id: 1,
                fullName: "Alex Johnson",
                email: "demo@nexura.health",
                password: "Demo@123",
                dateOfBirth: "1985-04-15",
                primaryCondition: "Type 2 Diabetes & Hypertension",
                emergencyCaregiverName: "Sarah Johnson",
                caregiverPhoneNumber: "+1 (555) 234-5678",
                aiProvider: "mock",
                groqKey: "", geminiKey: "", openaiKey: ""
            });
            DB.set("users", users);
        }
    }

    console.info("[Nexura Mock API] ✅ Initialized — All API calls will be handled locally.");
    console.info("[Nexura Mock API] 📋 Demo credentials: demo@nexura.health / Demo@123");
})();
