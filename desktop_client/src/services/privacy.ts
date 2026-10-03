import { ref } from 'vue';

export interface PrivacySettings {
  collectAnalytics: boolean;
  keepSearchHistory: boolean;
  keepBrowseHistory: boolean;
  // Security & App Lock
  pinLockEnabled: boolean;
  pinCode: string;
  lockTimeoutMinutes: number; // 0: immediate on blur, 1, 5, 15, -1: manual
  blurOnWindowBlur: boolean;
  panicModeEnabled: boolean;
  // Screenshot Privacy & Blur for Sharing
  screenshotPrivacyEnabled: boolean;
  blurImages: boolean;
  blurDescriptions: boolean;
}

const PRIVACY_KEY = 'gpdb_privacy_settings';

function loadPrivacySettings(): PrivacySettings {
  const defaults: PrivacySettings = {
    collectAnalytics: true,
    keepSearchHistory: true,
    keepBrowseHistory: true,
    pinLockEnabled: false,
    pinCode: '',
    lockTimeoutMinutes: 5,
    blurOnWindowBlur: false,
    panicModeEnabled: true,
    screenshotPrivacyEnabled: false,
    blurImages: true,
    blurDescriptions: true,
  };

  try {
    const raw = localStorage.getItem(PRIVACY_KEY);
    if (raw) {
      return {
        ...defaults,
        ...JSON.parse(raw),
      };
    }
  } catch {}
  return defaults;
}

export const privacySettings = ref<PrivacySettings>(loadPrivacySettings());

export function savePrivacySettings(updates: Partial<PrivacySettings>) {
  privacySettings.value = { ...privacySettings.value, ...updates };
  localStorage.setItem(PRIVACY_KEY, JSON.stringify(privacySettings.value));
}

export function toggleScreenshotPrivacy() {
  savePrivacySettings({
    screenshotPrivacyEnabled: !privacySettings.value.screenshotPrivacyEnabled,
  });
}

// Runtime Security States
export const isAppLocked = ref<boolean>(false);
export const isWindowBlurred = ref<boolean>(false);

let lastActiveTime = Date.now();

export function updateLastActiveTime() {
  lastActiveTime = Date.now();
}

export function lockApp() {
  if (privacySettings.value.pinLockEnabled && privacySettings.value.pinCode.length >= 4) {
    isAppLocked.value = true;
  }
}

export function unlockApp(pin: string): boolean {
  if (!privacySettings.value.pinLockEnabled) {
    isAppLocked.value = false;
    return true;
  }
  if (pin === privacySettings.value.pinCode) {
    isAppLocked.value = false;
    updateLastActiveTime();
    return true;
  }
  return false;
}

export function initPrivacyListeners() {
  // Window Focus / Blur Listener
  window.addEventListener('blur', () => {
    if (privacySettings.value.blurOnWindowBlur) {
      isWindowBlurred.value = true;
    }
    if (privacySettings.value.pinLockEnabled && privacySettings.value.lockTimeoutMinutes === 0) {
      lockApp();
    }
  });

  window.addEventListener('focus', () => {
    isWindowBlurred.value = false;
    if (privacySettings.value.pinLockEnabled && privacySettings.value.lockTimeoutMinutes > 0) {
      const elapsedMinutes = (Date.now() - lastActiveTime) / 1000 / 60;
      if (elapsedMinutes >= privacySettings.value.lockTimeoutMinutes) {
        lockApp();
      }
    }
  });

  // User activity tracker
  ['mousedown', 'keydown', 'scroll', 'touchstart'].forEach((event) => {
    window.addEventListener(event, () => updateLastActiveTime(), { passive: true });
  });
}
