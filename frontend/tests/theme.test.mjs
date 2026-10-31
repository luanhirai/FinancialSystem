import test from "node:test";
import assert from "node:assert/strict";
import vm from "node:vm";
import {themeBootstrap,normalizeTheme,THEME_KEY} from "../src/lib/theme.mjs";
function boot(storage){const document={documentElement:{dataset:{}}};vm.runInNewContext(themeBootstrap,{document,localStorage:storage});return document.documentElement.dataset.theme;}
test("restores saved preference before paint and rejects invalid values",()=>{
 for(const value of [null,"light","dark","invalid",'<script>']){
  const actual=boot({getItem(key){assert.equal(key,THEME_KEY);return value;}});
  assert.equal(actual,value==="dark"?"dark":"light");
  assert.equal(normalizeTheme(value),actual);
 }
});
test("blocked local storage still renders the default light theme",()=>{
 assert.equal(boot({getItem(){throw Error("Storage denied");}}),"light");
});
