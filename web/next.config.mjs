/** @type {import('next').NextConfig} */

/**
 * The folder a GitHub Pages *project* site lives under, read from the same
 * address the crawler is given. A user site (`owner.github.io`) and a custom
 * domain at the root both end up with an empty prefix, which is Next's default.
 *
 * `lib/site.ts` computes the identical string from the same variable; this file
 * is plain JS and cannot import it.
 */
const basePath = (() => {
  const url = process.env.NEXT_PUBLIC_SITE_URL
  if (!url) return ''
  const { pathname } = new URL(url)
  return pathname === '/' ? '' : pathname.replace(/\/$/, '')
})()

const nextConfig = {
  reactStrictMode: true,
  /** Without this the exported HTML asks for `/_next/...` at the domain root
   *  and a project page ships a blank white site. */
  basePath,
  assetPrefix: basePath || undefined,
  /**
   * The page has no server side of its own: the language switch is browser state
   * and the download is a file in `public/`. Exporting to static files means the
   * same build can be dropped on any host, and the APK link cannot rot behind a
   * runtime that is not there.
   */
  output: 'export',
  trailingSlash: true,
  /**
   * A static export has no server to resize images on, and the one picture on
   * this page is already delivered at the size it is shown. Turning the
   * optimiser off keeps the build honest instead of silently emitting a broken
   * /_next/image URL.
   */
  images: { unoptimized: true }
}

export default nextConfig
