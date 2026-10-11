import type { MetadataRoute } from 'next'
import { pages, siteUrl } from '@/lib/site'

/**
 * The list a crawler walks. `trailingSlash: true` means `/privacy/` is the
 * served address and `/privacy` a redirect, so the entries here are the ones
 * that resolve on the first request - a sitemap full of redirects reads as a
 * site that moves.
 */
export default function sitemap(): MetadataRoute.Sitemap {
  return pages.map(page => ({
    url: `${siteUrl}${page.path}`,
    lastModified: new Date(),
    changeFrequency: page.changeFrequency,
    priority: page.priority
  }))
}
