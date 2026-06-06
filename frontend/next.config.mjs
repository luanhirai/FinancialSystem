/** @type {import('next').NextConfig} */
const nextConfig = {
  /* config options here */
  allowedDevOrigins: ["world-chevy-attractions-foot.trycloudflare.com"],
  reactCompiler: true,
  turbopack: {
    root: import.meta.dirname,
  },
};

export default nextConfig;
