<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ platform: 'douyin' | 'xiaohongshu' }>()
const platformName = computed(() => props.platform === 'douyin' ? '抖音' : '小红书')
</script>

<template>
  <section class="page-heading">
    <div>
      <p class="eyebrow">分享空间 / {{ platformName }}</p>
      <h1>{{ platformName }}</h1>
      <p class="page-description">{{ platformName }}的分享内容将在这里管理。</p>
    </div>
  </section>

  <section class="panel message message--empty" :aria-label="`${platformName}分享`">
    <h2>{{ platformName }}分享</h2>
    <p>功能尚未接入，后续可在此扩展。</p>
  </section>
</template>
