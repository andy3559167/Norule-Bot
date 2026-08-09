<script setup lang="ts">
defineProps<{
  modelValue: boolean
  label: string
  description?: string
}>()

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
</script>

<template>
  <label class="nr-toggle-row">
    <span class="nr-toggle-copy">
      <strong>{{ label }}</strong>
      <small v-if="description">{{ description }}</small>
    </span>
    <input
      type="checkbox"
      :checked="modelValue"
      @change="emit('update:modelValue', ($event.target as HTMLInputElement).checked)"
    />
    <span class="nr-switch" aria-hidden="true"><i /></span>
  </label>
</template>
