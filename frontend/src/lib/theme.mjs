export const THEME_KEY="finsys-theme";
export const normalizeTheme=value=>value==="dark"?"dark":"light";
// Runs in the head before paint; only known theme names can reach the DOM.
export const themeBootstrap=`try{document.documentElement.dataset.theme=localStorage.getItem("finsys-theme")==="dark"?"dark":"light"}catch{document.documentElement.dataset.theme="light"}`;
