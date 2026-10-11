import type { MetadataRoute } from 'next'
import { siteUrl } from '@/lib/site'

/**
 * Nothing on this site is private and the whole argument is wasted if nobody
 * can find it, so the answer to every crawler is yes - with the sitemap handed
 * over in the same file, which is how a single-page site gets discovered at all
 * instead of only through somebody pasting the link.
 */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: [{ userAgent: '*', allow: '/' }],
    sitemap: `${siteUrl}/sitemap.xml`
  }
}
