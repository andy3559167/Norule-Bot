<script setup lang="ts">
import { computed, useId } from 'vue'

defineOptions({ inheritAttrs: false })
const value = defineModel<string>({ default: '' })
const props = withDefaults(defineProps<{ id?: string; label: string; hint?: string; error?: string; disabled?: boolean }>(), { hint: '', error: '', disabled: false })
const generatedId = useId()
const selectId = computed(() => props.id || generatedId)
const describedBy = computed(() => [props.hint && `${selectId.value}-hint`, props.error && `${selectId.value}-error`].filter(Boolean).join(' ') || undefined)
</script>

<template>
  <label class="nr-select" :for="selectId">
    <span class="nr-select__label">{{ label }}</span>
    <select :id="selectId" v-model="value" v-bind="$attrs" :disabled="disabled" :aria-invalid="Boolean(error)" :aria-describedby="describedBy"><slot /></select>
    <span v-if="hint" :id="`${selectId}-hint`" class="nr-select__hint">{{ hint }}</span>
    <span v-if="error" :id="`${selectId}-error`" class="nr-select__error" role="alert">{{ error }}</span>
  </label>
</template>

<style scoped>
.nr-select{display:grid;gap:.55rem}.nr-select__label{font-size:.88rem;font-weight:680}select{width:100%;min-height:var(--nr-control-height);padding:0 2.6rem 0 1rem;color:var(--nr-text);border:1px solid var(--nr-border);border-radius:var(--nr-radius-md);outline:0;background:#090e0b;cursor:pointer}select:focus{border-color:var(--nr-border-strong);box-shadow:0 0 0 3px rgba(112,230,171,.08)}select:disabled{cursor:not-allowed;opacity:.55}.nr-select__hint,.nr-select__error{font-size:.78rem;line-height:1.45}.nr-select__hint{color:var(--nr-text-muted)}.nr-select__error{color:var(--nr-danger)}
</style>
