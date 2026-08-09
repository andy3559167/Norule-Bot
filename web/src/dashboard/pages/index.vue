<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import type { DashboardTab } from '../types/dashboard'
import { useDashboard } from '../composables/useDashboard'
import GeneralSettings from '../components/tabs/GeneralSettings.vue'
import NotificationsSettings from '../components/tabs/NotificationsSettings.vue'
import LogsSettings from '../components/tabs/LogsSettings.vue'
import MusicSettings from '../components/tabs/MusicSettings.vue'
import PrivateRoomSettings from '../components/tabs/PrivateRoomSettings.vue'
import WelcomeSettings from '../components/tabs/WelcomeSettings.vue'
import NumberChainSettings from '../components/tabs/NumberChainSettings.vue'
import TicketSettings from '../components/tabs/TicketSettings.vue'

const dashboard = useDashboard()

const components = {
  general: GeneralSettings,
  notifications: NotificationsSettings,
  logs: LogsSettings,
  music: MusicSettings,
  privateRoom: PrivateRoomSettings,
  welcome: WelcomeSettings,
  numberChain: NumberChainSettings,
  ticket: TicketSettings,
}

const tabMeta: Record<DashboardTab, { title: string; description: string }> = {
  general: { title: '一般設定', description: '管理語言與這個伺服器的基礎偏好。' },
  notifications: { title: '通知設定', description: '整理成員與語音事件的頻道、訊息及樣式。' },
  logs: { title: '日誌紀錄', description: '設定管理事件的記錄位置與排除規則。' },
  music: { title: '音樂設定', description: '調整播放行為、指令頻道並查看使用統計。' },
  privateRoom: { title: '私人房間', description: '建立成員可自行管理的動態語音空間。' },
  welcome: { title: '歡迎訊息', description: '設計新成員加入時看到的第一則訊息。' },
  numberChain: { title: '數字接龍', description: '管理遊戲頻道、啟用狀態與目前進度。' },
  ticket: { title: '票券系統', description: '設定客服票券面板、類型、權限與歷史紀錄。' },
}

const activeMeta = computed(() => tabMeta[dashboard.state.currentTab])
const activeComponent = computed(() => components[dashboard.state.currentTab])
const enabledModuleCount = computed(() => [
  dashboard.state.settings.notifications.enabled,
  dashboard.state.settings.messageLogs.enabled,
  dashboard.state.settings.music.autoplayEnabled,
  dashboard.state.settings.privateRoom.enabled,
  dashboard.state.settings.welcome.enabled,
  dashboard.state.settings.numberChain.enabled,
  dashboard.state.settings.ticket.enabled,
].filter(Boolean).length)

const quickSettings = [
  { tab: 'welcome' as DashboardTab, icon: '✦', tone: 'violet', title: '歡迎訊息', description: '設定新成員加入時的歡迎頻道與內容。' },
  { tab: 'logs' as DashboardTab, icon: '▤', tone: 'blue', title: '管理日誌', description: '記錄訊息、頻道、身分組與管理操作。' },
  { tab: 'notifications' as DashboardTab, icon: '◉', tone: 'amber', title: '通知中心', description: '集中管理成員與語音活動通知。' },
]

function initializeFromHash() {
  const hash = window.location.hash.replace('#', '') as DashboardTab
  if (hash && hash in components) dashboard.state.currentTab = hash
}

onMounted(async () => {
  initializeFromHash()
  window.addEventListener('beforeunload', dashboard.beforeUnload)
  await dashboard.initialize()
})

onBeforeUnmount(() => window.removeEventListener('beforeunload', dashboard.beforeUnload))
</script>

<template>
  <div class="nr-dashboard">
    <div class="nr-stars" aria-hidden="true"><i /><i /><i /></div>

    <template v-if="dashboard.state.authenticating">
      <main class="nr-auth-page"><section class="nr-auth-card nr-loading-card"><span class="nr-brand-mark">N<i /></span><div class="nr-loading-line" /><div class="nr-loading-line is-short" /><div class="nr-loading-block" /></section></main>
    </template>

    <template v-else-if="!dashboard.state.authenticated">
      <main class="nr-auth-page">
        <section class="nr-auth-card">
          <div class="nr-brand is-centered"><span class="nr-brand-mark">N<i /></span><span><strong>NoRule</strong><small>BOT CONSOLE</small></span></div>
          <span class="nr-auth-kicker">DISCORD SERVER MANAGEMENT</span>
          <h1>掌握伺服器，<em>不必迷路。</em></h1>
          <p>以 Discord 安全登入後，集中管理 NoRule Bot 的通知、音樂、票券、歡迎訊息與自動化設定。</p>
          <a class="nr-button is-primary is-large" href="/auth/login">使用 Discord 登入 <span>→</span></a>
          <small class="nr-auth-note">只有具備「管理伺服器」權限的伺服器會出現在控制台。</small>
        </section>
      </main>
    </template>

    <template v-else>
      <DashboardSidebar />
      <div class="nr-workspace">
        <DashboardTopbar />
        <main class="nr-content">
          <template v-if="dashboard.manageableGuilds.value.length && dashboard.state.selectedGuildId">
            <header class="nr-page-head">
              <div><p>控制台 / <span>{{ activeMeta.title }}</span></p><h1>{{ activeMeta.title }}</h1><h2>{{ activeMeta.description }}</h2></div>
              <div class="nr-page-head-actions"><button type="button" class="nr-button is-secondary" :disabled="dashboard.state.loadingSection" @click="dashboard.loadSection(dashboard.state.currentTab, true)">↻ 重新載入</button><button type="button" class="nr-button is-primary" :disabled="dashboard.state.saving || !dashboard.hasDirtyChanges.value" @click="dashboard.saveSettings">{{ dashboard.state.saving ? '儲存中…' : '儲存變更' }}</button></div>
            </header>

            <section class="nr-status-card">
              <div class="nr-bot-avatar">N<i /></div>
              <div class="nr-bot-info"><div><h3>NoRule Bot</h3><span>● 運作正常</span></div><p>已連線至 {{ dashboard.currentGuild.value?.name }}，可使用控制面板管理現有模組設定。</p></div>
              <div class="nr-status-stats"><div><small>伺服器</small><strong>1 <em>個</em></strong></div><div><small>可設定模組</small><strong>8 <em>項</em></strong></div><div><small>目前啟用</small><strong>{{ enabledModuleCount }} <em>項</em></strong></div></div>
            </section>

            <section v-if="dashboard.state.currentTab === 'general'" class="nr-quick-area">
              <header class="nr-subsection-head"><div><h2>快速設定</h2><p>直接前往最常使用的伺服器功能</p></div></header>
              <div class="nr-quick-grid">
                <button v-for="item in quickSettings" :key="item.tab" type="button" class="nr-quick-card" @click="dashboard.switchTab(item.tab)">
                  <span class="nr-setting-icon" :class="`is-${item.tone}`">{{ item.icon }}</span><i>前往設定 →</i><h3>{{ item.title }}</h3><p>{{ item.description }}</p>
                </button>
              </div>
            </section>

            <div class="nr-settings-region" :class="{ 'is-loading': dashboard.state.loadingSection }">
              <div v-if="dashboard.state.loadingSection" class="nr-section-loader"><span /><p>正在讀取 {{ activeMeta.title }}…</p></div>
              <component :is="activeComponent" v-else />
            </div>

            <div v-if="dashboard.hasDirtyChanges.value" class="nr-save-dock">
              <span><i /> 有 {{ dashboard.state.dirtySections.size }} 個分頁包含未儲存的變更</span>
              <button type="button" class="nr-button is-primary" :disabled="dashboard.state.saving" @click="dashboard.saveSettings">{{ dashboard.state.saving ? '儲存中…' : '儲存全部變更' }}</button>
            </div>
          </template>

          <section v-else class="nr-no-guilds">
            <span class="nr-setting-icon is-violet">◇</span><h1>還沒有可管理的 NoRule 伺服器</h1><p>你擁有管理權限的伺服器會列在下方。先邀請 Bot，完成後再重新整理清單。</p>
            <button type="button" class="nr-button is-secondary" @click="dashboard.loadGuilds">重新整理伺服器</button>
            <div class="nr-invite-grid"><article v-for="guild in dashboard.invitationalGuilds.value" :key="guild.id"><span class="nr-guild-mini">{{ guild.name.slice(0, 2).toUpperCase() }}</span><div><strong>{{ guild.name }}</strong><small>Bot 尚未加入</small></div><a class="nr-button is-primary" :href="guild.inviteUrl" target="_blank" rel="noopener">邀請 Bot</a></article></div>
          </section>
        </main>
      </div>

      <DashboardModal :open="dashboard.state.guildDirectoryOpen" title="你的 Discord 伺服器" description="管理已加入 NoRule Bot 的伺服器，或將 Bot 邀請到其他伺服器。" @close="dashboard.state.guildDirectoryOpen = false">
        <div class="nr-guild-directory">
          <article v-for="guild in dashboard.state.guilds" :key="guild.id" :class="{ active: guild.id === dashboard.state.selectedGuildId }">
            <img v-if="guild.iconUrl" :src="guild.iconUrl" :alt="guild.name" referrerpolicy="no-referrer" /><span v-else class="nr-guild-mini">{{ guild.name.slice(0, 2).toUpperCase() }}</span>
            <div><strong>{{ guild.name }}</strong><small>{{ guild.botInGuild ? guild.botCanManage ? 'Bot 已就緒' : 'Bot 權限可能不足' : 'Bot 尚未加入' }}</small></div>
            <button v-if="guild.botInGuild" type="button" class="nr-button" :class="{ 'is-primary': guild.id !== dashboard.state.selectedGuildId }" @click="dashboard.selectGuild(guild.id)">{{ guild.id === dashboard.state.selectedGuildId ? '管理中' : '管理' }}</button>
            <a v-else class="nr-button is-primary" :href="guild.inviteUrl" target="_blank" rel="noopener">邀請 Bot</a>
          </article>
        </div>
        <template #footer><button type="button" class="nr-button" @click="dashboard.state.guildDirectoryOpen = false">關閉</button><button type="button" class="nr-button is-secondary" :disabled="dashboard.state.loadingGuilds" @click="dashboard.loadGuilds">{{ dashboard.state.loadingGuilds ? '重新整理中…' : '重新整理清單' }}</button></template>
      </DashboardModal>
    </template>
  </div>
</template>
