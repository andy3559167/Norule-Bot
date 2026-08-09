<script setup lang="ts">
withDefaults(defineProps<{ id?: string; eyebrow?: string; title?: string; description?: string; align?: 'left' | 'center' }>(), { id: undefined, eyebrow: '', title: '', description: '', align: 'left' })
</script>

<template>
  <section :id="id" class="nr-section" :class="`nr-section--${align}`">
    <div v-if="eyebrow || title || description" class="nr-section__heading">
      <p v-if="eyebrow" class="nr-section__eyebrow">{{ eyebrow }}</p>
      <h2 v-if="title">{{ title }}</h2>
      <p v-if="description" class="nr-section__description">{{ description }}</p>
    </div>
    <slot />
  </section>
</template>

<style scoped>
.nr-section{padding:clamp(4.5rem,9vw,7.5rem) 0}.nr-section__heading{display:grid;max-width:42rem;gap:.8rem;margin-bottom:2.3rem}.nr-section--center .nr-section__heading{margin-right:auto;margin-left:auto;text-align:center}.nr-section__eyebrow{margin:0;color:var(--nr-accent);font-size:.76rem;font-weight:800;letter-spacing:.15em;text-transform:uppercase}.nr-section h2{margin:0;font-size:clamp(2rem,5vw,3.5rem);font-weight:760;line-height:1.08;letter-spacing:-.045em}.nr-section__description{margin:0;color:var(--nr-text-muted);font-size:clamp(1rem,2vw,1.12rem)}
</style>
