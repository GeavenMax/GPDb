import { ref } from 'vue';
import { api } from '../api';

export type IconSchemeId = 'scheme-a' | 'scheme-d';

export interface IconScheme {
  id: IconSchemeId;
}

export const ICON_SCHEMES: IconScheme[] = [
  { id: 'scheme-a' },
  { id: 'scheme-d' },
];

const STORAGE_KEY = 'gpdb_selected_app_icon';

function getInitialIconScheme(): IconSchemeId {
  const saved = localStorage.getItem(STORAGE_KEY);
  if (saved === 'scheme-a' || saved === 'scheme-d') {
    return saved;
  }
  return 'scheme-a';
}

export const currentIconScheme = ref<IconSchemeId>(getInitialIconScheme());

export function setIconScheme(id: IconSchemeId) {
  currentIconScheme.value = id;
  localStorage.setItem(STORAGE_KEY, id);

  // Update dynamic favicon in document head
  updateFavicon(id);

  // Update macOS Dock icon immediately via native Cocoa main-thread dispatch
  api.setDockIcon(id).catch(err => {
    console.warn('Failed to update dock icon:', err);
  });
}

export function updateFavicon(schemeId: IconSchemeId) {
  const link: HTMLLinkElement =
    document.querySelector("link[rel*='icon']") || document.createElement('link');
  link.type = 'image/svg+xml';
  link.rel = 'shortcut icon';
  link.href = `/src/assets/icons/${schemeId}.svg`;
  document.getElementsByTagName('head')[0].appendChild(link);
}

export function initAppIcon() {
  const current = currentIconScheme.value;
  updateFavicon(current);
  // Immediately synchronize the user's selected icon scheme to the macOS Dock on startup
  api.setDockIcon(current).catch(err => {
    console.warn('Failed to sync dock icon on launch:', err);
  });
}
