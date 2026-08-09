<script setup lang="ts">
import type { DashboardTabDefinition } from '../types/dashboard'
import { useDashboard } from '../composables/useDashboard'

const dashboard = useDashboard()

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
</script>

<template>
  <aside class="nr-sidebar" :class="{ 'is-open': dashboard.state.sidebarOpen }">
    <button type="button" class="nr-sidebar-scrim" aria-label="關閉選單" @click="dashboard.state.sidebarOpen = false" />
    <div class="nr-sidebar-panel">
      <div class="nr-brand">
        <span class="nr-brand-mark">N<i /></span>
        <span><strong>NoRule</strong><small>BOT CONSOLE</small></span>
      </div>

      <nav class="nr-nav" aria-label="控制面板功能">
        <p>工作區</p>
        <button
          v-for="tab in tabs"
          :key="tab.id"
          type="button"
          class="nr-nav-item"
          :class="{ active: dashboard.state.currentTab === tab.id }"
          @click="dashboard.switchTab(tab.id)"
        >
          <span class="nr-nav-icon">{{ tab.icon }}</span>
          <span><strong>{{ dashboard.i18n.t(tab.labelKey, tab.fallback) }}</strong><small>{{ tab.caption }}</small></span>
          <i v-if="dashboard.state.dirtySections.has(tab.id)" class="nr-dirty-dot" title="有未儲存的變更" />
        </button>
      </nav>

      <div class="nr-sidebar-foot">
        <button type="button" class="nr-server-directory" @click="dashboard.state.guildDirectoryOpen = true">
          <span class="nr-guild-mini">{{ dashboard.currentGuild.value?.name?.slice(0, 2).toUpperCase() || 'NR' }}</span>
          <span><strong>{{ dashboard.currentGuild.value?.name || '選擇伺服器' }}</strong><small>管理與邀請 Bot</small></span>
          <i>↗</i>
        </button>
        <div v-if="dashboard.state.user" class="nr-account">
          <img v-if="dashboard.state.user.avatarUrl" :src="dashboard.state.user.avatarUrl" :alt="dashboard.state.user.username" referrerpolicy="no-referrer" />
          <span v-else class="nr-avatar-fallback">{{ dashboard.state.user.username.slice(0, 1).toUpperCase() }}</span>
          <span><strong>{{ dashboard.state.user.username }}</strong><small>Discord 管理者</small></span>
          <a href="/auth/logout" aria-label="登出">↪</a>
        </div>
      </div>
    </div>
  </aside>
</template>
