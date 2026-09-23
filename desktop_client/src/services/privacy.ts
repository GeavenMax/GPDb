import { ref } from 'vue';

export interface PrivacySettings {
  collectAnalytics: boolean;
  keepSearchHistory: boolean;
  keepBrowseHistory: boolean;
}

const PRIVACY_KEY = 'gpdb_privacy_settings';

function loadPrivacySettings(): PrivacySettings {
  try {
    const raw = localStorage.getItem(PRIVACY_KEY);
    if (raw) {
      return {
        collectAnalytics: true,
        keepSearchHistory: true,
        keepBrowseHistory: true,
        ...JSON.parse(raw)
      };
    }
  } catch {}
  return {
    collectAnalytics: true,
    keepSearchHistory: true,
    keepBrowseHistory: true
  };
}

export const privacySettings = ref<PrivacySettings>(loadPrivacySettings());

export function savePrivacySettings(updates: Partial<PrivacySettings>) {
  privacySettings.value = { ...privacySettings.value, ...updates };
  localStorage.setItem(PRIVACY_KEY, JSON.stringify(privacySettings.value));
}
