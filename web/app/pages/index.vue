<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

const navLinks = [
  { label: '縮短網址', href: '#shorten' },
  { label: '功能特色', href: '#features' },
  { label: '媒體分享', href: '#media' },
]

type ToolName = 'shorten' | 'media'
const activeTool = ref<ToolName>('shorten')

function selectTool(tool: ToolName, updateHash = true) {
  activeTool.value = tool
  if (updateHash && import.meta.client) history.replaceState(null, '', tool === 'media' ? '#media' : '#shorten')
}

function syncToolFromHash() {
  if (window.location.hash === '#media') selectTool('media', false)
  if (window.location.hash === '#shorten') selectTool('shorten', false)
}

function handleNavigation(link: { href: string }) {
  if (link.href === '#media') selectTool('media', false)
  if (link.href === '#shorten') selectTool('shorten', false)
}

onMounted(() => {
  syncToolFromHash()
  window.addEventListener('hashchange', syncToolFromHash)
})
onBeforeUnmount(() => window.removeEventListener('hashchange', syncToolFromHash))

const features = [
  { number: '01', title: '一秒完成縮短', description: '貼上完整網址，即刻產生簡潔、方便傳送的分享連結。' },
  { number: '02', title: '自訂好記短碼', description: '設定容易辨識的短碼，讓每一個連結更符合分享情境。' },
  { number: '03', title: '圖片與影片分享', description: '直接上傳支援的圖片或影片，再用一條乾淨的連結分享。' },
  { number: '04', title: '到期與密碼保護', description: '設定固定或自訂期限，並可用密碼限制媒體內容的存取。' },
]
</script>

<template>
  <div id="top" class="home-page">
    <NrNavbar brand="NoRule URL" :links="navLinks" @navigate="handleNavigation" />

    <main>
      <NrPageContainer>
        <section class="hero" aria-labelledby="hero-title">
          <div class="hero__copy">
            <NrBadge tone="accent">簡潔分享，即刻完成</NrBadge>
            <h1 id="hero-title">分享更短，<span>傳得更遠。</span></h1>
            <p>貼上你的網址，立即產生簡潔、好記且方便分享的短網址。</p>
          </div>
          <div class="hero__tool">
            <div class="tool-switch" role="tablist" aria-label="選擇分享工具">
              <button id="shorten-tab" type="button" role="tab" :aria-selected="activeTool === 'shorten'" aria-controls="shorten" :tabindex="activeTool === 'shorten' ? 0 : -1" @click="selectTool('shorten')" @keydown.right.prevent="selectTool('media')">縮短網址</button>
              <button id="media-tab" type="button" role="tab" :aria-selected="activeTool === 'media'" aria-controls="media" :tabindex="activeTool === 'media' ? 0 : -1" @click="selectTool('media')" @keydown.left.prevent="selectTool('shorten')">媒體分享</button>
            </div>
            <div id="shorten" v-show="activeTool === 'shorten'" class="tool-panel" role="tabpanel" aria-labelledby="shorten-tab">
              <div class="tool-panel__heading">
                <NrBadge tone="accent">網址縮短</NrBadge>
                <div><h2>貼上網址，立即縮短。</h2><p>可選擇自訂短碼，建立簡潔、容易辨識的分享連結。</p></div>
              </div>
              <ShortUrlForm />
            </div>
            <div id="media" v-show="activeTool === 'media'" class="tool-panel" role="tabpanel" aria-labelledby="media-tab">
              <div class="tool-panel__heading">
                <NrBadge tone="accent">媒體分享</NrBadge>
                <div><h2>一條連結，分享你的媒體。</h2><p>選擇檔案、到期時間與密碼保護，上傳完成後即可複製分享。</p></div>
              </div>
              <MediaShareForm />
            </div>
          </div>
          <div class="hero__note" aria-label="服務特色摘要"><span>免登入即可使用</span><span>自訂短碼</span><span>媒體到期保護</span></div>
        </section>

        <NrSection id="features" eyebrow="Why NoRule URL" title="分享工具，專注在真正需要的事。" description="不加入多餘流程。從一條網址到一個受保護的媒體連結，每一步都保持快速清楚。">
          <div class="feature-grid">
            <NrCard v-for="feature in features" :key="feature.number" as="article" tone="soft" padding="md" class="feature-card">
              <span class="feature-card__number">{{ feature.number }}</span>
              <h3>{{ feature.title }}</h3>
              <p>{{ feature.description }}</p>
            </NrCard>
          </div>
        </NrSection>

        <section class="bottom-cta" aria-labelledby="cta-title">
          <NrBadge tone="accent">NoRule URL</NrBadge>
          <h2 id="cta-title">下一個連結，不必再那麼長。</h2>
          <p>回到上方貼上網址，幾秒內完成分享。</p>
          <a href="#shorten" @click="selectTool('shorten', false)">開始縮短網址 <span aria-hidden="true">↑</span></a>
        </section>
      </NrPageContainer>
    </main>

    <NrFooter brand="NoRule URL" description="簡潔、好記，為每一次分享保留剛好的控制。"><a href="#top">回到頂部</a></NrFooter>
  </div>
</template>

<style scoped>
.home-page{position:relative;overflow:hidden}.home-page::before{position:absolute;z-index:-1;top:27rem;left:50%;width:min(92vw,76rem);height:1px;transform:translateX(-50%);background:linear-gradient(90deg,transparent,var(--nr-border),transparent);content:""}.hero{display:grid;min-height:calc(100vh - 4.8rem);align-content:center;justify-items:center;padding:clamp(4rem,10vh,7.5rem) 0 3.5rem;text-align:center}.hero__copy{display:grid;max-width:var(--nr-reading-width);justify-items:center;gap:1.25rem}.hero h1{margin:0;font-size:clamp(3.1rem,8vw,6.6rem);font-weight:770;line-height:.96;letter-spacing:-.065em}.hero h1 span{color:var(--nr-accent)}.hero__copy>p{max-width:37rem;margin:0;color:var(--nr-text-muted);font-size:clamp(1.05rem,2.3vw,1.3rem)}.hero__tool{width:min(100%,55rem);margin-top:2.4rem;text-align:left}.tool-switch{display:grid;grid-template-columns:1fr 1fr;width:min(100%,29rem);margin:0 auto 1.15rem;padding:.28rem;border:1px solid rgba(210,235,220,.08);border-radius:999px;background:#101412}.tool-switch button{min-height:3.7rem;padding:0 1.25rem;border:0;border-radius:999px;color:var(--nr-text-muted);background:transparent;font-size:1rem;font-weight:650;cursor:pointer;transition:color 160ms ease,background 160ms ease,box-shadow 160ms ease}.tool-switch button[aria-selected="true"]{color:var(--nr-text);background:#202522;box-shadow:0 8px 24px rgba(0,0,0,.24)}.tool-panel{display:grid;gap:1rem}.tool-panel__heading{display:grid;justify-items:start;gap:.65rem;padding:1rem .25rem 0;text-align:left}.tool-panel__heading>div{display:grid;max-width:36rem;gap:.35rem}.tool-panel__heading h2{margin:0;font-size:clamp(1.5rem,4vw,2.25rem);line-height:1.1;letter-spacing:-.035em}.tool-panel__heading p{margin:0;color:var(--nr-text-muted);font-size:.88rem}.hero__note{display:flex;flex-wrap:wrap;justify-content:center;gap:.65rem 1.5rem;margin-top:1.4rem;color:var(--nr-text-muted);font-size:.76rem}.hero__note span::before{margin-right:.45rem;color:var(--nr-accent);content:"•"}.feature-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:1rem}.feature-card{min-height:15rem;transition:transform 180ms ease,border-color 180ms ease,background 180ms ease}.feature-card:hover{transform:translateY(-4px);border-color:var(--nr-border-strong);background:rgba(112,230,171,.045)}.feature-card__number{color:var(--nr-accent);font-size:.72rem;font-weight:800;letter-spacing:.12em}.feature-card h3{margin:3.5rem 0 .65rem;font-size:1.13rem}.feature-card p{margin:0;color:var(--nr-text-muted);font-size:.9rem}.bottom-cta{display:grid;justify-items:center;margin:3rem 0 7rem;padding:clamp(3rem,8vw,5.5rem) 1.5rem;border:1px solid var(--nr-border);border-radius:var(--nr-radius-xl);background:linear-gradient(140deg,rgba(112,230,171,.08),rgba(255,255,255,.016) 55%);text-align:center;box-shadow:var(--nr-shadow-lg)}.bottom-cta h2{max-width:44rem;margin:1.2rem 0 .6rem;font-size:clamp(2.2rem,6vw,4.8rem);line-height:1;letter-spacing:-.055em}.bottom-cta p{margin:0;color:var(--nr-text-muted)}.bottom-cta>a{display:inline-flex;min-height:3.2rem;align-items:center;gap:.65rem;margin-top:1.8rem;padding:0 1.2rem;border-radius:var(--nr-radius-md);color:var(--nr-accent-ink);background:var(--nr-accent);font-weight:750;text-decoration:none}.bottom-cta>a:hover{background:var(--nr-accent-hover)}@media(max-width:900px){.feature-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:640px){.hero{min-height:auto;padding-top:4.5rem}.hero h1{font-size:clamp(3rem,15vw,4.3rem)}.tool-switch{width:100%}.tool-switch button{min-height:3.35rem;font-size:.9rem}.hero__note{display:grid;gap:.35rem}.feature-grid{grid-template-columns:1fr}.feature-card{min-height:auto}.feature-card h3{margin-top:2.2rem}.bottom-cta{margin-bottom:4rem}}
</style>
