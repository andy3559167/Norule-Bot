<script setup lang="ts">
import { computed, useId } from 'vue'

const value = defineModel<string>({ default: '' })
const props = withDefaults(defineProps<{
  id?: string
  label: string
  type?: string
  placeholder?: string
  hint?: string
  error?: string
  required?: boolean
  disabled?: boolean
  autocomplete?: string
  min?: string
  max?: string
}>(), { type: 'text', placeholder: '', hint: '', error: '', required: false, disabled: false, autocomplete: 'off', min: undefined, max: undefined })
const generatedId = useId()
const inputId = computed(() => props.id || generatedId)
const describedBy = computed(() => [props.hint && `${inputId.value}-hint`, props.error && `${inputId.value}-error`].filter(Boolean).join(' ') || undefined)
</script>

<template>
  <div class="nr-field">
    <label class="nr-field__label" :for="inputId">{{ label }}<span v-if="!required" class="nr-field__optional" aria-hidden="true">選填</span></label>
    <span class="nr-field__control" :class="{ 'nr-field__control--error': error }">
      <span v-if="$slots.prefix" class="nr-field__prefix"><slot name="prefix" /></span>
      <input :id="inputId" v-model="value" :type="type" :placeholder="placeholder" :required="required" :disabled="disabled" :autocomplete="autocomplete" :min="min" :max="max" :aria-invalid="Boolean(error)" :aria-describedby="describedBy">
      <span v-if="$slots.suffix" class="nr-field__suffix"><slot name="suffix" /></span>
    </span>
    <span v-if="hint" :id="`${inputId}-hint`" class="nr-field__hint">{{ hint }}</span>
    <span v-if="error" :id="`${inputId}-error`" class="nr-field__error" role="alert">{{ error }}</span>
  </div>
</template>

<style scoped>
.nr-field{display:grid;gap:.55rem;color:var(--nr-text)}.nr-field__label{display:flex;align-items:center;justify-content:space-between;gap:1rem;font-size:.88rem;font-weight:680}.nr-field__optional{color:var(--nr-text-muted);font-size:.76rem;font-weight:500}.nr-field__control{display:flex;min-height:var(--nr-control-height);align-items:center;overflow:hidden;border:1px solid var(--nr-border);border-radius:var(--nr-radius-md);background:rgba(5,9,7,.72);transition:border-color 160ms ease,box-shadow 160ms ease}.nr-field__control:focus-within{border-color:var(--nr-border-strong);box-shadow:0 0 0 3px rgba(112,230,171,.08)}.nr-field__control--error{border-color:color-mix(in srgb,var(--nr-danger) 55%,transparent)}input{min-width:0;width:100%;height:calc(var(--nr-control-height) - 2px);padding:0 1rem;color:var(--nr-text);border:0;outline:0;background:transparent}input::placeholder{color:#657168}input:disabled{cursor:not-allowed;opacity:.55}.nr-field__prefix{flex:none;padding-left:1rem;color:var(--nr-text-muted);font-size:.91rem;white-space:nowrap}.nr-field__suffix{display:flex;flex:none;padding-right:.42rem}.nr-field__hint,.nr-field__error{font-size:.78rem;line-height:1.45}.nr-field__hint{color:var(--nr-text-muted)}.nr-field__error{color:var(--nr-danger)}@media(max-width:640px){.nr-field__prefix{max-width:42vw;overflow:hidden;text-overflow:ellipsis}}
</style>
