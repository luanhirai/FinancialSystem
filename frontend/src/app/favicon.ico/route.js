const icon = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
  <rect width="64" height="64" rx="12" fill="#111827"/>
  <path d="M16 44V18h34v8H26v7h19v8H26v3h24v8H16z" fill="#22c55e"/>
</svg>`;

export function GET() {
  return new Response(icon, {
    headers: {
      "Content-Type": "image/svg+xml",
      "Cache-Control": "public, max-age=86400",
    },
  });
}
