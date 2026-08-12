import { NextResponse } from "next/server";

const API_URL =
  process.env.BACKEND_URL ||
  "https://faqs-combo-went-teaching.trycloudflare.com";
const publicRoutes = new Set(["/", "/login", "/register"]);

async function isAuthenticated(request) {
  const cookie = request.headers.get("cookie") || "";

  try {
    const response = await fetch(`${API_URL}/auth/me`, {
      headers: { cookie },
      cache: "no-store"
    });

    return response.ok;
  } catch {
    return false;
  }
}

function redirectToLogin(request, pathname) {
  const loginUrl = new URL("/login", request.url);
  loginUrl.searchParams.set("redirect", pathname);

  const response = NextResponse.redirect(loginUrl);
  response.cookies.delete("token");

  return response;
}

export async function proxy(request) {
  const { pathname } = request.nextUrl;
  const token = request.cookies.get("token")?.value;
  const isPublicRoute = publicRoutes.has(pathname);

  if (!token && !isPublicRoute) {
    return redirectToLogin(request, pathname);
  }

  if (token && (pathname === "/login" || pathname === "/register")) {
    if (await isAuthenticated(request)) {
      return NextResponse.redirect(new URL("/dashboard", request.url));
    }

    const response = NextResponse.next();
    response.cookies.delete("token");
    return response;
  }

  if (token && !isPublicRoute && !(await isAuthenticated(request))) {
    return redirectToLogin(request, pathname);
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    "/",
    "/login",
    "/register",
    "/dashboard/:path*",
    "/products/:path*",
    "/importacao-tiny/:path*",
    "/ecommerce/:path*",
    "/profile/:path*",
    "/rules_configuration/:path*",
    "/reports/:path*",
    "/settings/:path*"
  ]
};
