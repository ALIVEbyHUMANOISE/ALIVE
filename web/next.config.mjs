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
  trailingSlash: true
}

export default nextConfig
