import "element-plus/dist/index.css";
import "element-plus/theme-chalk/dark/css-vars.css";
import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import router from "./router";
import { i18n } from "./i18n";
import { elementPlusIcons } from "./plugins/elementPlusIcons";
import { useUiPreferencesStore } from "./stores/uiPreferences";
import "./styles/global.css";

const app = createApp(App);
const pinia = createPinia();

for (const [name, comp] of Object.entries(elementPlusIcons)) {
  app.component(name, comp);
}

app.use(pinia);
app.use(i18n);
useUiPreferencesStore(pinia).hydrate();
app.use(router);
app.mount("#app");
