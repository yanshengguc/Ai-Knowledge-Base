import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import i18n from './i18n'
import router from './router'
import './styles/global.scss'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(i18n)
// Element Plus 组件/图标/API 均改为 unplugin 按需自动引入(见 vite.config.ts);
// ElLoading 由 AutoImport 注入,这里显式注册以启用 v-loading 指令。
app.use(ElLoading)
app.mount('#app')
