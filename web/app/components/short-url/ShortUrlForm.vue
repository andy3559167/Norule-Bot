<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

const targetUrl = ref('')
const customCode = ref('')
const clientError = ref('')
const host = ref('此網域')
const { status, result, error, create } = useShortUrl()
const clipboard = useClipboard()

const shownStatus = computed(() => clientError.value ? 'idle' : status.value)
const shownError = computed(() => error.value)
const codePrefix = computed(() => `${host.value}/`)

onMounted(() => { host.value = window.location.host })
watch([targetUrl, customCode], () => { clientError.value = '' })

async function submit() {
  clientError.value = ''
  const value = targetUrl.value.trim()
  if (!value) {
    clientError.value = '請輸入要縮短的網址。'
    return
  }
  try {
    const parsed = new URL(value)
    if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error()
  } catch {
    clientError.value = '請輸入完整的 http:// 或 https:// 網址。'
    return
  }
  await create(value, customCode.value)
}
</script>

<template>
  <NrCard as="div" tone="strong" padding="lg" class="short-form-card">
    <form novalidate @submit.prevent="submit">
      <div class="short-form-card__primary">
        <NrInput id="target-url" v-model="targetUrl" label="要縮短的網址" type="url" placeholder="https://example.com/very/long/url" autocomplete="url" required :error="clientError" />
        <NrButton type="submit" :loading="status === 'loading'" :disabled="!targetUrl.trim()">縮短</NrButton>
      </div>
      <NrInput id="custom-code" v-model="customCode" label="自訂短碼" placeholder="好記的短碼">
        <template #prefix>{{ codePrefix }}</template>
      </NrInput>
    </form>
    <NrResultCard :status="shownStatus" title="短網址建立完成" :url="result?.shortUrl" :meta="result ? `前往 ${result.targetUrl}` : ''" :error="shownError" :copy-state="clipboard.state.value" @copy="result && clipboard.copy(result.shortUrl)" />
  </NrCard>
</template>

<style scoped>
.short-form-card{overflow:hidden}.short-form-card::before{position:absolute;inset:0 auto auto 10%;width:80%;height:1px;background:linear-gradient(90deg,transparent,var(--nr-accent),transparent);content:"";opacity:.55}form{display:grid;gap:1.1rem}.short-form-card__primary{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:end;gap:.75rem}.short-form-card__primary :deep(.nr-button){min-width:7.2rem}@media(max-width:640px){.short-form-card__primary{grid-template-columns:1fr}.short-form-card__primary :deep(.nr-button){width:100%}}
</style>
