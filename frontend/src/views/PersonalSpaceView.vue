<script setup lang="ts">
import personalAvatar from '@/assets/personal-avatar.png'
</script>

<template>
  <div class="personal-space">
    <div class="personal-space__portrait">
      <img :src="personalAvatar" alt="李冉使用的蓝白色动漫头像" />
    </div>

    <div class="personal-space__content">
      <p class="personal-space__kicker">星析 · StarAnaly</p>
      <h1>李冉的个人空间</h1>
      <p class="personal-space__lead">关于这个项目，以及它的使用方式。</p>

      <section class="personal-space__statement" aria-labelledby="personal-use-title">
        <h2 id="personal-use-title">项目说明</h2>
        <ul>
          <li>该项目仅用于李冉个人使用，不进行任何商业用途。</li>
          <li>禁止贩卖该项目。</li>
          <li>允许其他人下载后修改，但仍然禁止售卖。</li>
        </ul>
      </section>

      <div class="personal-space__repository">
        <span>项目地址</span>
        <a href="https://github.com/bilibilixms/StarAnaly-Arbiter" target="_blank" rel="noopener noreferrer">
          github.com/bilibilixms/StarAnaly-Arbiter
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M13 5h6v6M19 5l-9 9" />
            <path d="M19 13v5a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1h5" />
          </svg>
        </a>
      </div>
    </div>
  </div>
</template>
