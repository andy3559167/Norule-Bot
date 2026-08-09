<script setup lang="ts">
export interface NavbarLink { label: string; href: string }
withDefaults(defineProps<{ brand?: string; links?: NavbarLink[] }>(), { brand: 'NoRule', links: () => [] })
defineEmits<{ navigate: [link: NavbarLink] }>()
</script>

<template>
  <header class="nr-navbar">
    <NrPageContainer>
      <nav aria-label="主要導覽">
        <a class="nr-navbar__brand" href="#top" aria-label="回到頁面頂端"><span aria-hidden="true" />{{ brand }}</a>
        <div class="nr-navbar__links">
          <a v-for="link in links" :key="link.href" :href="link.href" @click="$emit('navigate', link)">{{ link.label }}</a>
        </div>
        <slot name="action" />
      </nav>
    </NrPageContainer>
  </header>
</template>

<style scoped>
.nr-navbar{position:sticky;z-index:20;top:0;border-bottom:1px solid rgba(210,235,220,.08);background:rgba(8,11,10,.78);backdrop-filter:blur(18px)}nav{display:flex;min-height:4.8rem;align-items:center;justify-content:space-between;gap:2rem}.nr-navbar__brand{display:inline-flex;align-items:center;gap:.7rem;font-weight:780;letter-spacing:-.025em;text-decoration:none}.nr-navbar__brand span{width:.7rem;height:.7rem;border-radius:50%;background:var(--nr-accent);box-shadow:0 0 20px rgba(112,230,171,.7)}.nr-navbar__links{display:flex;align-items:center;gap:clamp(1rem,3vw,2.3rem)}.nr-navbar__links a{color:var(--nr-text-muted);font-size:.88rem;font-weight:620;text-decoration:none;transition:color 160ms ease}.nr-navbar__links a:hover{color:var(--nr-text)}@media(max-width:640px){nav{min-height:4.2rem;gap:.65rem}.nr-navbar__brand{gap:.45rem;font-size:.86rem}.nr-navbar__links{gap:.58rem}.nr-navbar__links a{font-size:.69rem;white-space:nowrap}}
</style>
