// Nexura AI Chatbot Logic with Multilingual and Voice Assistance

let recognition = null;
let voiceOutputEnabled = false;
let currentUtterance = null;
let currentUser = null;

document.addEventListener("DOMContentLoaded", () => {
    // 1. Render Navigation Template
    renderSidebar("chatbot");

    // 2. Load Patient & Chat Details
    loadChatbotPortal();

    // 3. Initialize Speech Recognition
    initSpeechRecognition();

    // 4. Initialize Mute state visual
    updateVoiceOutputUI();
});

async function loadChatbotPortal() {
    try {
        const userResp = await fetchWithAuth("/api/auth/me");
        if (!userResp) return;
        const user = await userResp.json();
        currentUser = user;

        // Update headers and widgets
        document.getElementById("profile-name").innerText = user.fullName;
        const avatar = user.fullName.split(" ").map(n => n[0]).join("").substring(0, 2).toUpperCase();
        document.getElementById("user-avatar").innerText = avatar;

        document.getElementById("caregiver-name-widget").innerText = user.emergencyCaregiverName;
        document.getElementById("caregiver-phone-widget").innerText = `Caregiver: ${user.caregiverPhoneNumber}`;

        await fetchChatHistory();
        await fetchAIContextRisk();
    } catch (err) {
        showToast("Error connecting to companion engine.", "error");
    }
}

async function fetchChatHistory() {
    try {
        const resp = await fetchWithAuth("/api/chat/history");
        if (!resp) return;
        const data = await resp.json();

        const history = document.getElementById("chat-history");
        history.innerHTML = "";

        if (data.length === 0) {
            history.innerHTML = `
                <div class="chat-bubble ai">
                    Hello! I am Nexura, your personal healthcare companion. How are you feeling today? I have access to your daily health history, sleep journals, and medications to support you empathetically.
                </div>
            `;
            return;
        }

        data.forEach(turn => {
            // User Message
            const userBubble = document.createElement("div");
            userBubble.className = "chat-bubble user";
            userBubble.innerText = turn.message;
            history.appendChild(userBubble);

            // AI Message
            const aiBubble = document.createElement("div");
            aiBubble.className = "chat-bubble ai";
            
            const span = document.createElement("span");
            span.innerText = turn.aiResponse;
            aiBubble.appendChild(span);

            const speakBtn = document.createElement("button");
            speakBtn.className = "speaker-read-btn";
            speakBtn.title = "Read Aloud";
            speakBtn.innerText = "🔊";
            speakBtn.addEventListener("click", () => speakMessage(speakBtn, turn.aiResponse, turn.language));
            aiBubble.appendChild(speakBtn);

            history.appendChild(aiBubble);
        });

        scrollToBottom();
    } catch (err) {
        showToast("Error loading conversation history.", "error");
    }
}

async function fetchAIContextRisk() {
    try {
        const resp = await fetchWithAuth("/api/ai/risk-status");
        if (!resp) return;
        const data = await resp.json();

        const badge = document.getElementById("companion-risk-badge");
        badge.innerText = `RISK ASSESSMENT: ${data.riskLevel}`;

        // Color coordination based on risk levels
        badge.className = `risk-banner risk-${data.riskLevel}`;
        if (data.riskLevel === "LOW") {
            badge.style.color = "#065f46";
            badge.style.backgroundColor = "var(--success-light)";
        } else if (data.riskLevel === "MEDIUM") {
            badge.style.color = "#92400e";
            badge.style.backgroundColor = "var(--warning-light)";
        } else {
            badge.style.color = "#991b1b";
            badge.style.backgroundColor = "var(--danger-light)";
        }

        // Synchronize adaptive header emotion based on risk level initial state
        updateHeaderEmotion(data.riskLevel);
    } catch (err) {
        // Handled
    }
}

function updateHeaderEmotion(riskOrEmotion) {
    const header = document.getElementById("chatbot-header");
    if (!header) return;

    // Reset styles
    header.className = "chat-header";
    
    // Add emotion mapping
    let emotionClass = "emotion-NORMAL";
    if (riskOrEmotion === "CRITICAL") emotionClass = "emotion-CRITICAL";
    else if (riskOrEmotion === "HIGH" || riskOrEmotion === "URGENT") emotionClass = "emotion-URGENT";
    else if (riskOrEmotion === "MEDIUM" || riskOrEmotion === "EMPATHETIC") emotionClass = "emotion-EMPATHETIC";

    header.classList.add(emotionClass);
}

async function sendChatMessage() {
    const input = document.getElementById("chat-message-input");
    const msg = input.value.trim();
    if (!msg) return;

    input.value = "";
    await processChatMessage(msg);
}

async function sendSuggestionChip(promptText) {
    await processChatMessage(promptText);
}

async function processChatMessage(msg) {
    const history = document.getElementById("chat-history");

    // 1. Append User Message
    const userBubble = document.createElement("div");
    userBubble.className = "chat-bubble user";
    userBubble.innerText = msg;
    history.appendChild(userBubble);
    scrollToBottom();

    // 2. Append simulated typing bubble
    const typing = document.createElement("div");
    typing.className = "typing-indicator";
    typing.innerHTML = `
        <div class="typing-dot"></div>
        <div class="typing-dot"></div>
        <div class="typing-dot"></div>
    `;
    history.appendChild(typing);
    scrollToBottom();

    const selectedLang = document.getElementById("language-select").value;

    try {
        // Post message to backend with the standard payload format
        const resp = await fetchWithAuth("/api/chat/message", {
            method: "POST",
            body: JSON.stringify({ 
                message: msg,
                language: selectedLang,
                voiceMode: voiceOutputEnabled
            })
        });

        typing.remove();

        if (resp && resp.ok) {
            const data = await resp.json();
            
            // 3. Append AI response
            const aiBubble = document.createElement("div");
            aiBubble.className = "chat-bubble ai";
            
            const span = document.createElement("span");
            span.innerText = data.reply;
            aiBubble.appendChild(span);

            const speakBtn = document.createElement("button");
            speakBtn.className = "speaker-read-btn";
            speakBtn.title = "Read Aloud";
            speakBtn.innerText = "🔊";
            speakBtn.addEventListener("click", () => speakMessage(speakBtn, data.reply, selectedLang));
            aiBubble.appendChild(speakBtn);

            history.appendChild(aiBubble);
            scrollToBottom();

            // 4. Update adaptive styles based on returned emotion
            updateHeaderEmotion(data.emotionMode);

            // 5. Automatic TTS playback if speaker mode is enabled
            if (voiceOutputEnabled) {
                speakMessage(speakBtn, data.reply, selectedLang);
            }

            // Refresh risk context dynamically
            await fetchAIContextRisk();
        } else {
            showToast("Failed to fetch chatbot response.", "error");
            renderRetryOption(msg);
        }
    } catch (err) {
        typing.remove();
        console.error("Chat error details:", err);
        showToast("Error processing response: " + err.message, "error");
        renderRetryOption(msg);
    }
}

function renderRetryOption(failedMsg) {
    const history = document.getElementById("chat-history");
    const retryDiv = document.createElement("div");
    retryDiv.style.alignSelf = "center";
    retryDiv.style.margin = "12px 0";
    retryDiv.innerHTML = `
        <button class="btn btn-secondary" onclick="retryMessage(this, '${failedMsg.replace(/'/g, "\\'")}')" style="font-size:12px; padding: 6px 16px;">
            🔄 Retry failed message
        </button>
    `;
    history.appendChild(retryDiv);
    scrollToBottom();
}

function retryMessage(btnElement, msgText) {
    btnElement.parentElement.remove();
    processChatMessage(msgText);
}

function scrollToBottom() {
    const history = document.getElementById("chat-history");
    history.scrollTop = history.scrollHeight;
}

// --- MULTILINGUAL SPEECH RECOGNITION (STT) ---

function initSpeechRecognition() {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
        console.warn("Web Speech Recognition API is not supported in this browser.");
        document.getElementById("voice-input-btn").style.display = "none";
        return;
    }

    recognition = new SpeechRecognition();
    recognition.continuous = false;
    recognition.interimResults = false;

    recognition.onstart = () => {
        const btn = document.getElementById("voice-input-btn");
        btn.classList.add("listening");
        showToast("Listening... Speak now.", "info");
    };

    recognition.onresult = (event) => {
        const resultText = event.results[0][0].transcript;
        const input = document.getElementById("chat-message-input");
        input.value = resultText;
        showToast("Speech captured!", "success");
    };

    recognition.onerror = (event) => {
        console.error("Speech Recognition Error:", event.error);
        showToast(`Speech error: ${event.error}`, "error");
        stopListeningState();
    };

    recognition.onend = () => {
        stopListeningState();
    };
}

function toggleVoiceInput() {
    if (!recognition) {
        showToast("Voice input is not supported in your browser.", "warning");
        return;
    }

    const btn = document.getElementById("voice-input-btn");
    if (btn.classList.contains("listening")) {
        recognition.stop();
    } else {
        // Configure correct recognition language locale
        const selectedLang = document.getElementById("language-select").value;
        recognition.lang = getLocaleForLangCode(selectedLang);
        try {
            recognition.start();
        } catch (e) {
            recognition.stop();
        }
    }
}

function stopListeningState() {
    const btn = document.getElementById("voice-input-btn");
    if (btn) btn.classList.remove("listening");
}

function getLocaleForLangCode(code) {
    switch (code) {
        case "ta": return "ta-IN";
        case "hi": return "hi-IN";
        case "te": return "te-IN";
        case "ml": return "ml-IN";
        case "kn": return "kn-IN";
        case "fr": return "fr-FR";
        case "es": return "es-ES";
        case "de": return "de-DE";
        case "ja": return "ja-JP";
        default: return "en-US";
    }
}

// --- MULTILINGUAL SPEECH SYNTHESIS (TTS) ---

function toggleVoiceOutput() {
    voiceOutputEnabled = !voiceOutputEnabled;
    updateVoiceOutputUI();
    if (!voiceOutputEnabled) {
        stopSpeaking();
    } else {
        showToast("Speech output enabled. AI responses will be read aloud.", "success");
    }
}

function updateVoiceOutputUI() {
    const btn = document.getElementById("voice-output-toggle");
    if (voiceOutputEnabled) {
        btn.innerText = "🔊";
        btn.style.backgroundColor = "var(--primary-light)";
        btn.style.color = "var(--primary)";
    } else {
        btn.innerText = "🔇";
        btn.style.backgroundColor = "var(--bg-primary)";
        btn.style.color = "var(--text-muted)";
    }
}

function speakMessage(btn, text, langCode) {
    if (!window.speechSynthesis) {
        showToast("Speech Synthesis is not supported in this browser.", "warning");
        return;
    }

    if (btn && btn.classList.contains("playing")) {
        stopSpeaking();
        return;
    }

    stopSpeaking();

    currentUtterance = new SpeechSynthesisUtterance(text);
    
    // Assign language locale
    const locale = getLocaleForLangCode(langCode);
    currentUtterance.lang = locale;

    // Load available voices
    const voices = window.speechSynthesis.getVoices();
    let bestVoice = voices.find(v => v.lang.startsWith(locale));
    if (bestVoice) {
        currentUtterance.voice = bestVoice;
    }

    // Set button visual playing states
    if (btn) {
        btn.classList.add("playing");
        btn.innerText = "⏹️ Stop";
    }

    currentUtterance.onend = () => {
        if (btn) {
            btn.classList.remove("playing");
            btn.innerText = "🔊";
        }
        currentUtterance = null;
    };

    currentUtterance.onerror = () => {
        if (btn) {
            btn.classList.remove("playing");
            btn.innerText = "🔊";
        }
        currentUtterance = null;
    };

    window.speechSynthesis.speak(currentUtterance);
}

function stopSpeaking() {
    if (window.speechSynthesis) {
        window.speechSynthesis.cancel();
    }
    // Restore all speaker buttons to normal
    document.querySelectorAll(".speaker-read-btn").forEach(btn => {
        btn.classList.remove("playing");
        btn.innerText = "🔊";
    });
}

function onLanguageChange() {
    stopSpeaking();
    const badge = document.getElementById("detected-lang-badge");
    const langSelect = document.getElementById("language-select");
    
    if (langSelect.value === "auto") {
        badge.innerText = "Auto-Detecting";
        badge.style.display = "inline-block";
    } else {
        badge.style.display = "none";
    }
}

// --- CLEAR CHAT HISTORY ---

async function confirmClearHistory() {
    if (confirm("Are you sure you want to permanently clear your conversation history with Nexura?")) {
        try {
            const resp = await fetchWithAuth("/api/chat/history", {
                method: "DELETE"
            });
            if (resp && resp.ok) {
                showToast("Chat history successfully cleared.", "success");
                stopSpeaking();
                
                // Clear UI logs and restore hello card
                const history = document.getElementById("chat-history");
                history.innerHTML = `
                    <div class="chat-bubble ai">
                        Hello! I am Nexura, your personal healthcare companion. How are you feeling today? I have access to your daily health history, sleep journals, and medications to support you empathetically.
                    </div>
                `;
            } else {
                showToast("Failed to clear chat history.", "error");
            }
        } catch (e) {
            showToast("Server connection error.", "error");
        }
    }
}

// --- AI ENGINE SETTINGS MODAL HANDLERS ---

function openSettingsModal() {
    if (!currentUser) {
        showToast("User session not loaded yet. Please wait.", "warning");
        return;
    }
    document.getElementById("settings-provider-select").value = currentUser.aiProvider || "groq";
    document.getElementById("settings-groq-key").value = currentUser.groqKey || "";
    document.getElementById("settings-gemini-key").value = currentUser.geminiKey || "";
    document.getElementById("settings-openai-key").value = currentUser.openaiKey || "";
    toggleSettingsKeyFields();
    document.getElementById("settings-modal").classList.add("active");
}

function closeSettingsModal() {
    document.getElementById("settings-modal").classList.remove("active");
}

function toggleSettingsKeyFields() {
    const provider = document.getElementById("settings-provider-select").value;
    document.getElementById("settings-groq-key-group").style.display = provider === "groq" ? "block" : "none";
    document.getElementById("settings-gemini-key-group").style.display = provider === "gemini" ? "block" : "none";
    document.getElementById("settings-openai-key-group").style.display = provider === "openai" ? "block" : "none";
}

async function saveAISettings() {
    const provider = document.getElementById("settings-provider-select").value;
    const groqKey = document.getElementById("settings-groq-key").value.trim();
    const geminiKey = document.getElementById("settings-gemini-key").value.trim();
    const openaiKey = document.getElementById("settings-openai-key").value.trim();

    try {
        const resp = await fetchWithAuth("/api/auth/settings", {
            method: "POST",
            body: JSON.stringify({
                aiProvider: provider,
                groqKey: groqKey,
                geminiKey: geminiKey,
                openaiKey: openaiKey
            })
        });

        if (resp && resp.ok) {
            showToast("AI configuration saved successfully!", "success");
            closeSettingsModal();
            currentUser.aiProvider = provider;
            currentUser.groqKey = groqKey;
            currentUser.geminiKey = geminiKey;
            currentUser.openaiKey = openaiKey;
        } else {
            showToast("Failed to save AI settings.", "error");
        }
    } catch (err) {
        showToast("Connection error: " + err.message, "error");
    }
}
