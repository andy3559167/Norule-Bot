<script setup lang="ts">
withDefaults(defineProps<{ as?: string; tone?: 'default' | 'soft' | 'strong'; padding?: 'none' | 'sm' | 'md' | 'lg' }>(), { as: 'div', tone: 'default', padding: 'md' })
</script>

<template><component :is="as" class="nr-card" :class="[`nr-card--${tone}`, `nr-card--${padding}`]"><slot /></component></template>

<style scoped>
.nr-card{position:relative;border:1px solid var(--nr-border);border-radius:var(--nr-radius-lg);box-shadow:var(--nr-shadow-sm)}.nr-card--default{background:var(--nr-surface)}.nr-card--soft{background:var(--nr-surface-soft)}.nr-card--strong{background:var(--nr-surface-strong)}.nr-card--none{padding:0}.nr-card--sm{padding:var(--nr-space-5)}.nr-card--md{padding:var(--nr-space-8)}.nr-card--lg{padding:clamp(1.5rem,4vw,3rem)}
</style>
