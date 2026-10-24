import { defineConfig } from 'vitepress'

// https://vitepress.dev/reference/site-config
export default defineConfig({
  cleanUrls: true,
  lastUpdated: true,
  locales: {
    root: {
      label: '简体中文',
      lang: 'zh-CN',
      title: 'LuckyTool',
      description: 'ColorOS 系统增强模块使用文档',
      themeConfig: {
        nav: [
          { text: '指南', link: '/guide/', activeMatch: '/guide/' },
          { text: '更新日志', link: '/changelog' }
        ],
        sidebar: [
          {
            text: '指南',
            items: [
              { text: '简介', link: '/guide/' },
              { text: '安装与激活', link: '/guide/install' },
              { text: '功能列表', link: '/guide/features' },
              { text: '常见问题', link: '/guide/faq' }
            ]
          },
          {
            text: '其它',
            items: [
              { text: '更新日志', link: '/changelog' }
            ]
          }
        ],
        outline: { level: [2, 4], label: '本页目录' },
        docFooter: { prev: '上一页', next: '下一页' },
        lastUpdated: { text: '最后更新' },
        darkModeSwitchLabel: '外观',
        sidebarMenuLabel: '菜单',
        returnToTopLabel: '返回顶部',
        search: {
          provider: 'local'
        },
        footer: {
          message: '基于 <a href="https://vitepress.dev/">VitePress</a> 构建',
          copyright: 'Copyright © 2026 luckyzyx'
        }
      }
    },
    en: {
      label: 'English',
      lang: 'en',
      link: '/en/',
      title: 'LuckyTool',
      description: 'Documentation for the ColorOS system enhancement module',
      themeConfig: {
        nav: [
          { text: 'Changelog', link: '/en/changelog' },
          { text: '中文文档', link: '/guide/' }
        ],
        sidebar: [
          {
            text: 'Changelog',
            items: [
              { text: 'Changelog', link: '/en/changelog' }
            ]
          },
          {
            text: 'Documentation',
            items: [
              { text: '中文文档', link: '/guide/' }
            ]
          }
        ],
        outline: { level: [2, 4], label: 'On this page' },
        docFooter: { prev: 'Previous', next: 'Next' },
        lastUpdated: { text: 'Last updated' },
        darkModeSwitchLabel: 'Appearance',
        sidebarMenuLabel: 'Menu',
        returnToTopLabel: 'Return to top',
        search: {
          provider: 'local'
        },
        footer: {
          message: 'Built with <a href="https://vitepress.dev/">VitePress</a>',
          copyright: 'Copyright © 2026 luckyzyx'
        }
      }
    }
  }
})
