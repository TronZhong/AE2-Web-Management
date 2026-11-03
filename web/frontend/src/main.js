import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'

/** 应用入口：挂载根组件并接通路由。 */
createApp(App).use(router).mount('#app')
