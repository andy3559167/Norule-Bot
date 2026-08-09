<script setup lang="ts">
import { ref } from 'vue'
import type { DashboardTabDefinition } from '../types/dashboard'
import { useDashboard } from '../composables/useDashboard'

const dashboard = useDashboard()
const logoutConfirmOpen = ref(false)

const tabs: DashboardTabDefinition[] = [
  { id: 'general', labelKey: 'tabs_general', fallback: '一般設定', caption: '語言與基礎偏好', icon: '⌂' },
  { id: 'notifications', labelKey: 'tabs_notifications', fallback: '通知設定', caption: '成員與語音通知', icon: '◉' },
  { id: 'logs', labelKey: 'tabs_logs', fallback: '日誌紀錄', caption: '事件記錄與排除規則', icon: '▤' },
  { id: 'music', labelKey: 'tabs_music', fallback: '音樂設定', caption: '播放行為與統計', icon: '♫' },
  { id: 'privateRoom', labelKey: 'tabs_privateRoom', fallback: '私人房間', caption: '動態語音房間', icon: '◇' },
  { id: 'welcome', labelKey: 'tabs_welcome', fallback: '歡迎訊息', caption: '新成員迎賓流程', icon: '✦' },
  { id: 'numberChain', labelKey: 'tabs_numberChain', fallback: '數字接龍', caption: '頻道與遊戲進度', icon: '#' },
  { id: 'ticket', labelKey: 'tabs_ticket', fallback: '票券系統', caption: '客服流程與歷史', icon: '▣' },
]

function requestLogout() {
  dashboard.state.sidebarOpen = false
  logoutConfirmOpen.value = true
}

function logout() {
  window.location.assign('/auth/logout')
}
</script>

<template>
  <aside class="nr-sidebar" :class="{ 'is-open': dashboard.state.sidebarOpen }">
    <button type="button" class="nr-sidebar-scrim" aria-label="關閉選單" @click="dashboard.state.sidebarOpen = false" />
    <div class="nr-sidebar-panel">
      <button type="button" class="nr-brand" aria-label="返回控制台首頁" @click="dashboard.openHome">
        <span class="nr-brand-mark"><img v-if="dashboard.state.bot?.avatarUrl" :src="dashboard.state.bot.avatarUrl" :alt="dashboard.state.bot.username" referrerpolicy="no-referrer"><template v-else>N</template><i /></span>
        <span><strong>{{ dashboard.state.bot?.username || 'NoRule' }}</strong><small>BOT CONSOLE</small></span>
      </button>

      <nav class="nr-nav" aria-label="控制面板功能">
        <p>工作區</p>
        <button
          v-for="tab in tabs"
          :key="tab.id"
          type="button"
          class="nr-nav-item"
          :class="{ active: !dashboard.state.atHome && dashboard.state.currentTab === tab.id }"
          @click="dashboard.switchTab(tab.id)"
        >
          <span class="nr-nav-icon">{{ tab.icon }}</span>
          <span><strong>{{ dashboard.i18n.t(tab.labelKey, tab.fallback) }}</strong><small>{{ tab.caption }}</small></span>
          <i v-if="dashboard.state.dirtySections.has(tab.id)" class="nr-dirty-dot" title="有未儲存的變更" />
        </button>
      </nav>

      <div class="nr-sidebar-foot">
        <button type="button" class="nr-server-directory" @click="dashboard.state.guildDirectoryOpen = true">
          <img v-if="dashboard.currentGuild.value?.iconUrl" class="nr-guild-mini is-image" :src="dashboard.currentGuild.value.iconUrl" :alt="dashboard.currentGuild.value.name" referrerpolicy="no-referrer">
          <span v-else class="nr-guild-mini">{{ dashboard.currentGuild.value?.name?.slice(0, 2).toUpperCase() || 'NR' }}</span>
          <span><strong>{{ dashboard.currentGuild.value?.name || '選擇伺服器' }}</strong><small>管理與邀請 Bot</small></span>
          <i>↗</i>
        </button>
        <div v-if="dashboard.state.user" class="nr-account">
          <img v-if="dashboard.state.user.avatarUrl" :src="dashboard.state.user.avatarUrl" :alt="dashboard.state.user.username" referrerpolicy="no-referrer" />
          <span v-else class="nr-avatar-fallback">{{ dashboard.state.user.username.slice(0, 1).toUpperCase() }}</span>
          <span><strong>{{ dashboard.state.user.username }}</strong><small>Discord 管理者</small></span>
          <button type="button" aria-label="登出" @click="requestLogout">↪</button>
        </div>
      </div>
    </div>
  </aside>

  <DashboardModal
    :open="logoutConfirmOpen"
    :title="dashboard.i18n.t('logout_confirm_title', '確認登出')"
    :description="dashboard.i18n.t('logout_confirm_description', '登出後，需要再次透過 Discord 驗證才能管理伺服器。')"
    @close="logoutConfirmOpen = false"
  >
    <div class="nr-logout-confirm">
      <span>↪</span>
      <div><strong>{{ dashboard.i18n.t('logout_confirm_question', '確定要登出 NoRule Bot 控制台嗎？') }}</strong><p v-if="dashboard.hasDirtyChanges.value">目前仍有未儲存的設定，登出後這些變更會遺失。</p></div>
    </div>
    <template #footer><button type="button" class="nr-button" @click="logoutConfirmOpen = false">取消</button><button type="button" class="nr-button is-danger" @click="logout">確認登出</button></template>
  </DashboardModal>
</template>
