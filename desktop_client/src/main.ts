import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import { openUrlExternal } from './services/pluginManager'

// Global interceptor for external links in Tauri WebView
if (typeof document !== 'undefined') {
  document.addEventListener('click', (e) => {
    const target = (e.target as HTMLElement)?.closest?.('a');
    if (target) {
      const href = target.getAttribute('href') || target.href;
      if (href && (href.startsWith('http://') || href.startsWith('https://'))) {
        e.preventDefault();
        openUrlExternal(href);
      }
    }
  });
}

createApp(App).mount('#app')
