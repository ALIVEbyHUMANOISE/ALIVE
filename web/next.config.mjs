/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
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
