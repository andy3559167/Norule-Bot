<script setup lang="ts">
import { useDashboard } from '../composables/useDashboard'

const dashboard = useDashboard()
</script>

<template>
  <div class="nr-toast-host" aria-live="polite" aria-atomic="true">
    <TransitionGroup name="nr-toast">
      <button
        v-for="toast in dashboard.state.toasts"
        :key="toast.id"
        type="button"
        class="nr-toast"
        :class="`is-${toast.tone}`"
        @click="dashboard.dismissToast(toast.id)"
      >
        <span class="nr-toast-dot" />
        <span>{{ toast.message }}</span>
        <i>×</i>
      </button>
    </TransitionGroup>
  </div>
</template>
