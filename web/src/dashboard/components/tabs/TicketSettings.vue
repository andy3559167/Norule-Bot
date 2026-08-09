<script setup lang="ts">
import { computed, ref } from 'vue'
import type { TicketTranscript } from '../../types/dashboard'
import { useDashboard } from '../../composables/useDashboard'

const dashboard = useDashboard()
const cfg = computed(() => dashboard.state.settings.ticket)
const sendingPanel = ref(false)
const historyLoading = ref(false)
const dirty = () => dashboard.markDirty('ticket')

const selectedOption = computed(() => {
  return cfg.value.options.find((option) => option.id === dashboard.state.selectedTicketOptionId)
    || cfg.value.options[0]
})

function selectOption(optionId: string) {
  dashboard.state.selectedTicketOptionId = optionId
}

async function sendPanel() {
  sendingPanel.value = true
  try { await dashboard.sendTicketPanel() } finally { sendingPanel.value = false }
}

async function reloadHistory() {
  historyLoading.value = true
  try { await dashboard.loadTicketHistory(true) } finally { historyLoading.value = false }
}

function formatBytes(size: number) {
  if (!Number.isFinite(size) || size <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.min(Math.floor(Math.log(size) / Math.log(1024)), units.length - 1)
  return `${(size / 1024 ** index).toFixed(index === 0 ? 0 : 1)} ${units[index]}`
}

function formatDate(timestamp: number) {
  if (!timestamp) return '-'
  return new Intl.DateTimeFormat(dashboard.i18n.state.language, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(timestamp))
}

function requestTranscriptDelete(file: TicketTranscript) {
  dashboard.state.pendingTranscriptDelete = file.name
}
</script>

<template>
  <div class="nr-stack">
    <DashboardSection eyebrow="Tickets" :title="dashboard.i18n.t('ticket_group_basic_title', '票券基本設定')" :description="dashboard.i18n.t('ticket_settings_lead', '管理票券模組、關閉政策與使用者建立方式。')">
      <template #actions><button type="button" class="nr-button is-danger-subtle" @click="dashboard.resetSection('ticket')">{{ dashboard.i18n.t('resetSectionBtn', '重設此區') }}</button></template>
      <div class="nr-toggle-grid"><DashboardToggle :model-value="cfg.enabled" :label="dashboard.i18n.t('t_enabled', '啟用票券系統')" description="允許成員從面板建立客服票券" @update:model-value="cfg.enabled = $event; dirty()" /></div>
      <div class="nr-form-grid is-three nr-form-block">
        <label class="nr-field"><span>{{ dashboard.i18n.t('t_autoCloseDays', '自動關閉天數') }}</span><input v-model.number="cfg.autoCloseDays" type="number" min="1" max="365" @input="dirty"><small>1–365 天</small></label>
        <label class="nr-field"><span>{{ dashboard.i18n.t('t_maxOpenPerUser', '每位成員可開啟上限') }}</span><input v-model.number="cfg.maxOpenPerUser" type="number" min="1" max="20" @input="dirty"><small>1–20 張票券</small></label>
        <label class="nr-field"><span>{{ dashboard.i18n.t('t_openUiMode', '建立介面') }}</span><select v-model="cfg.openUiMode" @change="dirty"><option value="BUTTONS">按鈕</option><option value="SELECT">下拉選單</option></select><small>依票券類型數量選擇合適介面</small></label>
      </div>
    </DashboardSection>

    <DashboardSection :title="dashboard.i18n.t('ticket_group_access_title', '權限與黑名單')" description="選擇可處理票券的身分組，並限制特定使用者建立票券。">
      <div class="nr-form-grid is-two">
        <label class="nr-field"><span>{{ dashboard.i18n.t('t_supportRoleIds', '客服身分組') }} <b class="nr-count-badge">{{ cfg.supportRoleIds.length }}</b></span><select v-model="cfg.supportRoleIds" multiple size="7" @change="dirty"><option v-for="role in dashboard.state.roles" :key="role.id" :value="role.id">{{ role.name }}</option></select><small>可同時選擇多個身分組</small></label>
        <label class="nr-field"><span>{{ dashboard.i18n.t('t_blacklistedUserIds', '黑名單使用者 ID') }}</span><textarea v-model="cfg.blacklistedUserIds" placeholder="123456789, 987654321" @input="dirty" /><small>多個 ID 請以逗號分隔</small></label>
      </div>
    </DashboardSection>

    <DashboardSection :title="dashboard.i18n.t('ticket_group_panel_title', '票券面板')" description="設定發佈到 Discord 的票券入口外觀與目標頻道。">
      <template #actions><button type="button" class="nr-button is-secondary" :disabled="sendingPanel || !cfg.panelChannelId" @click="sendPanel">{{ sendingPanel ? '傳送中…' : dashboard.i18n.t('sendTicketPanelBtn', '傳送票券面板') }}</button></template>
      <div class="nr-form-grid is-two">
        <label class="nr-field is-full"><span>{{ dashboard.i18n.t('t_panelChannelId', '面板頻道') }}</span><select v-model="cfg.panelChannelId" @change="dirty"><option value="">尚未設定</option><option v-for="channel in dashboard.state.textChannels" :key="channel.id" :value="channel.id"># {{ channel.name }}</option></select></label>
        <label class="nr-field is-full"><span>{{ dashboard.i18n.t('t_panelTitle', '面板標題') }}</span><input v-model="cfg.panelTitle" type="text" @input="dirty"></label>
        <label class="nr-field is-full"><span>{{ dashboard.i18n.t('t_panelDescription', '面板說明') }}</span><textarea v-model="cfg.panelDescription" @input="dirty" /></label>
        <label class="nr-color-field"><span>{{ dashboard.i18n.t('t_panelColor', '面板色彩') }}</span><input v-model="cfg.panelColor" type="color" @input="dirty"><b>{{ cfg.panelColor }}</b></label>
        <label class="nr-field"><span>{{ dashboard.i18n.t('t_panelButtonStyle', '按鈕樣式') }}</span><select v-model="cfg.panelButtonStyle" @change="dirty"><option value="PRIMARY">主要・藍</option><option value="SECONDARY">次要・灰</option><option value="SUCCESS">成功・綠</option><option value="DANGER">危險・紅</option></select><small>Discord 僅允許使用預設按鈕色彩</small></label>
        <label class="nr-field"><span>{{ dashboard.i18n.t('t_panelButtonLimit', '按鈕顯示上限') }}</span><input v-model.number="cfg.panelButtonLimit" type="number" min="1" max="25" @input="dirty"><small>允許範圍：1–25</small></label>
      </div>
    </DashboardSection>

    <DashboardSection :title="dashboard.i18n.t('ticket_group_form_title', '建立前表單')" description="在建立票券前先收集摘要，客服人員可更快理解需求。">
      <div class="nr-toggle-grid"><DashboardToggle :model-value="cfg.preOpenFormEnabled" :label="dashboard.i18n.t('t_preOpenFormEnabled', '要求填寫建立前表單')" @update:model-value="cfg.preOpenFormEnabled = $event; dirty()" /></div>
      <div class="nr-form-grid is-two nr-form-block">
        <label class="nr-field"><span>{{ dashboard.i18n.t('t_preOpenFormTitle', '表單標題') }}</span><input v-model="cfg.preOpenFormTitle" type="text" maxlength="45" @input="dirty"><small>最多 45 個字元</small></label>
        <label class="nr-field"><span>{{ dashboard.i18n.t('t_preOpenFormLabel', '欄位標籤') }}</span><input v-model="cfg.preOpenFormLabel" type="text" maxlength="45" @input="dirty"><small>最多 45 個字元</small></label>
        <label class="nr-field is-full"><span>{{ dashboard.i18n.t('t_preOpenFormPlaceholder', '輸入提示') }}</span><input v-model="cfg.preOpenFormPlaceholder" type="text" maxlength="100" @input="dirty"></label>
        <label class="nr-field is-full"><span>{{ dashboard.i18n.t('t_welcomeMessage', '票券歡迎訊息') }}</span><textarea v-model="cfg.welcomeMessage" @input="dirty" /><small>{user}、{type}、{summary}</small></label>
        <label class="nr-field is-full"><span>{{ dashboard.i18n.t('t_optionLabels', '相容票券選項文字') }}</span><input v-model="cfg.optionLabels" type="text" @input="dirty"><small>保留給既有設定使用；新的類型請在下一區個別管理</small></label>
      </div>
    </DashboardSection>

    <DashboardSection :title="dashboard.i18n.t('ticket_group_options_title', '票券類型')" :description="dashboard.i18n.t('t_optionEditor_hint', '每一種票券可設定獨立的面板文字、按鈕樣式、歡迎訊息與表單。')">
      <template #actions><button type="button" class="nr-button is-primary" @click="dashboard.addTicketOption">＋ {{ dashboard.i18n.t('t_addOptionBtn', '新增票券類型') }}</button><button type="button" class="nr-button is-danger-subtle" @click="dashboard.deleteTicketOption(dashboard.state.selectedTicketOptionId)">{{ dashboard.i18n.t('t_deleteOptionBtn', '刪除所選類型') }}</button></template>
      <div class="nr-ticket-option-layout">
        <div class="nr-ticket-option-list">
          <button v-for="option in cfg.options" :key="option.id" type="button" :class="{ active: option.id === dashboard.state.selectedTicketOptionId }" @click="selectOption(option.id)">
            <span class="nr-ticket-option-icon">▣</span><span><strong>{{ option.label || dashboard.i18n.t('ticket_option_unnamed', '未命名類型') }}</strong><small>{{ option.panelTitle || dashboard.i18n.t('ticket_default_panel_title', '票券面板') }}</small></span><b>{{ option.panelButtonStyle }}</b>
          </button>
        </div>
        <div v-if="selectedOption" class="nr-ticket-editor">
          <div class="nr-form-grid is-two">
            <label class="nr-field"><span>{{ dashboard.i18n.t('t_optionLabel', '類型名稱') }}</span><input v-model="selectedOption.label" type="text" @input="dirty"></label>
            <label class="nr-field"><span>{{ dashboard.i18n.t('t_optionButtonStyle', '按鈕樣式') }}</span><select v-model="selectedOption.panelButtonStyle" @change="dirty"><option value="PRIMARY">PRIMARY</option><option value="SECONDARY">SECONDARY</option><option value="SUCCESS">SUCCESS</option><option value="DANGER">DANGER</option></select></label>
            <label class="nr-field is-full"><span>{{ dashboard.i18n.t('t_optionPanelTitle', '面板標題') }}</span><input v-model="selectedOption.panelTitle" type="text" @input="dirty"></label>
            <label class="nr-field"><span>{{ dashboard.i18n.t('t_optionPanelDescription', '面板說明') }}</span><textarea v-model="selectedOption.panelDescription" @input="dirty" /></label>
            <label class="nr-field"><span>{{ dashboard.i18n.t('t_optionWelcomeMessage', '歡迎訊息') }}</span><textarea v-model="selectedOption.welcomeMessage" @input="dirty" /><small>{user}、{type}、{summary}</small></label>
          </div>
          <DashboardToggle :model-value="selectedOption.preOpenFormEnabled" :label="dashboard.i18n.t('t_optionPreOpenFormEnabled', '此類型啟用建立前表單')" @update:model-value="selectedOption.preOpenFormEnabled = $event; dirty()" />
          <div class="nr-form-grid is-two nr-form-block">
            <label class="nr-field"><span>{{ dashboard.i18n.t('t_optionPreOpenFormTitle', '表單標題') }}</span><input v-model="selectedOption.preOpenFormTitle" type="text" @input="dirty"></label>
            <label class="nr-field"><span>{{ dashboard.i18n.t('t_optionPreOpenFormLabel', '欄位標籤') }}</span><input v-model="selectedOption.preOpenFormLabel" type="text" @input="dirty"></label>
            <label class="nr-field is-full"><span>{{ dashboard.i18n.t('t_optionPreOpenFormPlaceholder', '輸入提示') }}</span><input v-model="selectedOption.preOpenFormPlaceholder" type="text" @input="dirty"></label>
          </div>
        </div>
      </div>
    </DashboardSection>

    <DashboardSection :title="dashboard.i18n.t('ticket_group_history_title', '票券歷史紀錄')" description="下載或刪除由票券系統產生的對話紀錄。">
      <template #actions><button type="button" class="nr-button is-secondary" :disabled="historyLoading" @click="reloadHistory">{{ historyLoading ? '載入中…' : dashboard.i18n.t('loadTicketHistoryBtn', '重新載入歷史') }}</button></template>
      <p class="nr-history-meta">共 {{ dashboard.state.ticketHistory.length }} 份紀錄・保留 {{ dashboard.state.ticketHistoryRetentionDays }} 天・本次清理 {{ dashboard.state.ticketHistoryCleaned }} 份</p>
      <div v-if="dashboard.state.ticketHistory.length" class="nr-history-list">
        <article v-for="file in dashboard.state.ticketHistory" :key="file.name">
          <span class="nr-history-icon">TXT</span>
          <div class="nr-history-copy"><a :href="file.url" target="_blank" rel="noopener">{{ file.name }}</a><small>頻道：{{ file.channelId ? `<#${file.channelId}>` : '-' }} ・ {{ formatDate(file.lastModifiedAt) }}</small></div>
          <b>{{ formatBytes(file.size) }}</b>
          <div class="nr-history-actions">
            <template v-if="dashboard.state.pendingTranscriptDelete === file.name"><button type="button" class="nr-button" @click="dashboard.state.pendingTranscriptDelete = ''">取消</button><button type="button" class="nr-button is-danger" @click="dashboard.deleteTranscript(file)">確認刪除</button></template>
            <button v-else type="button" class="nr-button is-danger-subtle" @click="requestTranscriptDelete(file)">刪除</button>
          </div>
        </article>
      </div>
      <div v-else class="nr-empty-state"><span>▤</span><strong>{{ dashboard.i18n.t('ticket_history_empty', '目前沒有票券歷史紀錄') }}</strong><p>建立並關閉票券後，對話紀錄會出現在這裡。</p></div>
    </DashboardSection>
  </div>
</template>
