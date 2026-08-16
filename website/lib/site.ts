/**
 * @file site.ts
 * @description Central configuration file for the website's metadata, links, and constant values.
 * @module lib/site
 */

/**
 * Global site configuration object.
 * Contains metadata, external links, and author information used throughout the application.
 */
export const siteConfig = {
  name: "mpvX",
  version: "v1.2.7",
  description:
    "Advanced mpv-based video player for Android with powerful features, seamless playback, and open-source freedom.",
  url: "https://mpvx.vercel.app",
  ogImage: "https://mpvx.vercel.app/og.jpg",
  icons: {
    icon: "/icon.svg",
    apple: "/apple-icon.png",
  },
  links: {
    github: "https://github.com/sfsakhawat999/mpvX",
    releases: "https://github.com/sfsakhawat999/mpvX/releases",
    latestRelease: "https://github.com/sfsakhawat999/mpvX/releases/latest",
    izzyOnAndroid: "https://apt.izzysoft.de/packages/xyz.mpv.rex",
    contributors: "https://github.com/sfsakhawat999/mpvX/graphs/contributors",
  },
  author: {
    name: "sfsakhawat999",
    url: "https://github.com/sfsakhawat999",
  },
} as const;

export type SiteConfig = typeof siteConfig;
