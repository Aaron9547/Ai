import * as ElementPlusIconsVue from "@element-plus/icons-vue";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import "element-plus/theme-chalk/dark/css-vars.css";
import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import router from "./router";
import { i18n } from "./i18n";
import { useUiPreferencesStore } from "./stores/uiPreferences";
import "./styles/global.css";

const app = createApp(App);
const pinia = createPinia();

for (const [name, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, comp);
}

app.use(pinia);
app.use(i18n);
useUiPreferencesStore(pinia).hydrate();
app.use(router);
app.use(ElementPlus, { size: "default" });
app.mount("#app");
