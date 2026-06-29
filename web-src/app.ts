// Dragonic Vernosa — web UI logic (TypeScript, di-compile ke app.js via tsc di CI)

interface AndroidBridge {
    checkModelStatus(): void;
    pickModelFile(): void;
    downloadModel(url: string): void;
    deleteModel(): void;
    sendPrompt(promptJson: string): void;
    stopGeneration(): void;
}

declare const AndroidBridge: AndroidBridge;

const chatLog = document.getElementById("chat-log") as HTMLDivElement;
const inputBox = document.getElementById("input-box") as HTMLTextAreaElement;
const sendBtn = document.getElementById("send-btn") as HTMLButtonElement;
const stopBtn = document.getElementById("stop-btn") as HTMLButtonElement;
const statusDot = document.getElementById("status-dot") as HTMLSpanElement;
const statusText = document.getElementById("status-text") as HTMLSpanElement;
const setupPanel = document.getElementById("setup-panel") as HTMLDivElement;
const importBtn = document.getElementById("import-btn") as HTMLButtonElement;
const downloadBtn = document.getElementById("download-btn") as HTMLButtonElement;
const urlInput = document.getElementById("url-input") as HTMLInputElement;
const progressBar = document.getElementById("progress-bar") as HTMLDivElement;
const progressWrap = document.getElementById("progress-wrap") as HTMLDivElement;
const settingsToggle = document.getElementById("settings-toggle") as HTMLButtonElement;
const deleteModelBtn = document.getElementById("delete-model-btn") as HTMLButtonElement;

let currentAssistantBubble: HTMLDivElement | null = null;
let isGenerating = false;
let modelReady = false;

function appendBubble(role: "user" | "assistant", text: string): HTMLDivElement {
    const bubble = document.createElement("div");
    bubble.className = `bubble ${role}`;
    bubble.textContent = text;
    chatLog.appendChild(bubble);
    chatLog.scrollTop = chatLog.scrollHeight;
    return bubble;
}

function setGeneratingState(active: boolean) {
    isGenerating = active;
    sendBtn.style.display = active ? "none" : "flex";
    stopBtn.style.display = active ? "flex" : "none";
    inputBox.disabled = active;
}

function setModelStatus(ready: boolean) {
    modelReady = ready;
    statusDot.className = ready ? "dot online" : "dot offline";
    statusText.textContent = ready ? "MODEL SIAP" : "MODEL BELUM DI-LOAD";
    setupPanel.style.display = ready ? "none" : "flex";
    sendBtn.disabled = !ready;
}

sendBtn.addEventListener("click", () => {
    const text = inputBox.value.trim();
    if (!text || !modelReady || isGenerating) return;

    appendBubble("user", text);
    inputBox.value = "";
    currentAssistantBubble = appendBubble("assistant", "");
    setGeneratingState(true);

    const payload = JSON.stringify({ prompt: text, maxTokens: 256, temperature: 0.8 });
    AndroidBridge.sendPrompt(payload);
});

inputBox.addEventListener("keydown", (e: KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        sendBtn.click();
    }
});

stopBtn.addEventListener("click", () => {
    AndroidBridge.stopGeneration();
});

importBtn.addEventListener("click", () => {
    AndroidBridge.pickModelFile();
});

downloadBtn.addEventListener("click", () => {
    const url = urlInput.value.trim();
    if (!url) return;
    progressWrap.style.display = "block";
    downloadBtn.disabled = true;
    AndroidBridge.downloadModel(url);
});

settingsToggle.addEventListener("click", () => {
    setupPanel.style.display = setupPanel.style.display === "none" ? "flex" : "none";
});

deleteModelBtn.addEventListener("click", () => {
    AndroidBridge.deleteModel();
});

// ====== Callback yang dipanggil dari Kotlin (MainActivity.runOnWebView) ======

(window as any).onModelStatus = (ready: boolean) => {
    setModelStatus(ready);
};

(window as any).onModelLoading = (loading: boolean) => {
    statusText.textContent = loading ? "LOADING MODEL..." : statusText.textContent;
    statusDot.className = loading ? "dot loading" : statusDot.className;
};

(window as any).onImportDone = (success: boolean, error: string | null) => {
    downloadBtn.disabled = false;
    progressWrap.style.display = "none";
    if (!success) {
        alert(`Gagal: ${error}`);
    }
};

(window as any).onDownloadProgress = (pct: number) => {
    progressBar.style.width = `${pct}%`;
};

(window as any).onToken = (token: string) => {
    if (currentAssistantBubble) {
        currentAssistantBubble.textContent += token;
        chatLog.scrollTop = chatLog.scrollHeight;
    }
};

(window as any).onGenDone = () => {
    setGeneratingState(false);
    currentAssistantBubble = null;
};

(window as any).onGenError = (message: string) => {
    setGeneratingState(false);
    if (currentAssistantBubble) {
        currentAssistantBubble.textContent += `\n[error: ${message}]`;
    }
};

// Init
AndroidBridge.checkModelStatus();
