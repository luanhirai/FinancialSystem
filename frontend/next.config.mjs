/** @type {import('next').NextConfig} */
const nextConfig = {
  poweredByHeader: false,
  /* config options here */
  reactCompiler: true,
  turbopack: {
    root: import.meta.dirname,
  },
};

export default nextConfig;
