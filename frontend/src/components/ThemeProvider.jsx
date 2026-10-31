"use client";
import { createContext, useContext, useEffect, useState } from "react";
import { THEME_KEY, normalizeTheme } from "@/lib/theme.mjs";
const ThemeContext=createContext(null);
export function ThemeProvider({children}){
 const [theme,setTheme]=useState("light"),[storageError,setStorageError]=useState(false);
 useEffect(()=>{
  setTheme(normalizeTheme(document.documentElement.dataset.theme));
  const sync=event=>{
   if(event.key!==THEME_KEY&&event.key!==null)return;
   const value=normalizeTheme(event.newValue);
   document.documentElement.dataset.theme=value;setTheme(value);
  };
  window.addEventListener("storage",sync);
  return()=>window.removeEventListener("storage",sync);
 },[]);
 const changeTheme=value=>{
  const next=normalizeTheme(value);document.documentElement.dataset.theme=next;setTheme(next);
  try{localStorage.setItem(THEME_KEY,next);setStorageError(false);}catch{setStorageError(true);}
 };
 return <ThemeContext.Provider value={{theme,changeTheme,storageError}}>{children}</ThemeContext.Provider>;
}
export function useTheme(){return useContext(ThemeContext);}
