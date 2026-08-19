/** @type {import('next').NextConfig} */
const nextConfig = {
  /* config options here */
  allowedDevOrigins: ["bradford-nontraditionary-danna.ngrok-free.dev"],
  reactCompiler: true,
  turbopack: {
    root: import.meta.dirname,
  },
};

export default nextConfig;
